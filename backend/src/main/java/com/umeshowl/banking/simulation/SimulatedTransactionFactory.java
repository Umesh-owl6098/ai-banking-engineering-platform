package com.umeshowl.banking.simulation;

import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SimulatedTransactionFactory {

    private static final List<String> HIGH_RISK_COUNTRIES = List.of(
            "Iran",
            "North Korea",
            "Syria",
            "Afghanistan"
    );

    private static final List<String> NORMAL_COUNTRIES = List.of(
            "United States",
            "United Kingdom",
            "Canada",
            "Germany",
            "France",
            "Singapore"
    );

    private static final List<String> CHANNELS = List.of(
            "ONLINE",
            "MOBILE",
            "BRANCH",
            "ATM",
            "CARD"
    );

    private static final List<String> TRANSACTION_TYPES = List.of(
            "TRANSFER",
            "WIRE",
            "ACH",
            "CARD_PAYMENT",
            "CASH_DEPOSIT",
            "INTERNATIONAL_TRANSFER"
    );

    private final MockCustomerRepository customerRepository;
    private final MockTransactionRepository transactionRepository;

    public SimulatedTransactionFactory(
            MockCustomerRepository customerRepository,
            MockTransactionRepository transactionRepository
    ) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<GeneratedTransaction> generate(SimulationScenario scenario) {
        SimulationScenario effectiveScenario = resolveScenario(scenario);

        return switch (effectiveScenario) {
            case STRUCTURING -> List.of(
                    persist(buildStructuringTransaction(pickCustomer(null)))
            );
            case RAPID_MOVEMENT -> persistRapidMovement();
            default -> List.of(
                    persist(buildSingleTransaction(
                            effectiveScenario,
                            pickCustomer(effectiveScenario)
                    ))
            );
        };
    }

    private SimulationScenario resolveScenario(SimulationScenario scenario) {
        if (scenario != SimulationScenario.MIXED) {
            return scenario;
        }

        SimulationScenario[] options = {
                SimulationScenario.NORMAL,
                SimulationScenario.LARGE_TRANSFER,
                SimulationScenario.STRUCTURING,
                SimulationScenario.RAPID_MOVEMENT,
                SimulationScenario.HIGH_RISK_COUNTRY,
                SimulationScenario.PEP_ACTIVITY,
                SimulationScenario.NEW_ACCOUNT_ACTIVITY
        };

        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    private List<GeneratedTransaction> persistRapidMovement() {
        MockCustomer customer = pickCustomer(SimulationScenario.RAPID_MOVEMENT);
        int count = ThreadLocalRandom.current().nextInt(3, 6);
        List<GeneratedTransaction> generated = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            generated.add(persist(buildRapidMovementTransaction(
                    customer,
                    index
            )));
        }

        return generated;
    }

    private GeneratedTransaction persist(MockTransaction draft) {
        MockTransaction saved = transactionRepository.save(draft);
        return new GeneratedTransaction(
                saved,
                SimulationScenario.valueOf(saved.getSimulationScenario())
        );
    }

    private MockTransaction buildSingleTransaction(
            SimulationScenario scenario,
            MockCustomer customer
    ) {
        return switch (scenario) {
            case NORMAL -> buildNormalTransaction(customer);
            case LARGE_TRANSFER -> buildLargeTransferTransaction(customer);
            case HIGH_RISK_COUNTRY -> buildHighRiskCountryTransaction(customer);
            case PEP_ACTIVITY -> buildPepActivityTransaction(customer);
            case NEW_ACCOUNT_ACTIVITY -> buildNewAccountActivityTransaction(
                    customer
            );
            default -> buildNormalTransaction(customer);
        };
    }

    private MockCustomer pickCustomer(SimulationScenario scenario) {
        List<MockCustomer> activeCustomers =
                customerRepository.findByAccountStatus("ACTIVE");

        if (activeCustomers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No active mock customers available for simulation"
            );
        }

        if (scenario == SimulationScenario.PEP_ACTIVITY) {
            return customerRepository.findByPepStatusNot("NONE")
                    .stream()
                    .filter(customer ->
                            "ACTIVE".equals(customer.getAccountStatus())
                    )
                    .findAny()
                    .orElse(activeCustomers.getFirst());
        }

        if (scenario == SimulationScenario.NEW_ACCOUNT_ACTIVITY) {
            LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(120);
            return activeCustomers.stream()
                    .filter(customer ->
                            customer.getAccountOpened() != null
                                    && customer.getAccountOpened()
                                            .isAfter(cutoff)
                    )
                    .findAny()
                    .orElse(activeCustomers.getFirst());
        }

        return activeCustomers.get(
                ThreadLocalRandom.current().nextInt(activeCustomers.size())
        );
    }

    private MockTransaction buildNormalTransaction(MockCustomer customer) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.NORMAL
        );
        transaction.setAmount(randomAmount(25, 2_500));
        transaction.setTransactionType(pick(TRANSACTION_TYPES));
        transaction.setChannel(pick(CHANNELS));
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(customer.getCountryOfResidence());
        transaction.setRiskScore(randomScore(3, 25));
        transaction.setFlagged(false);
        transaction.setRiskIndicators(new String[] { "ROUTINE_ACTIVITY" });
        transaction.setDescription("Routine customer payment");
        return transaction;
    }

    private MockTransaction buildLargeTransferTransaction(
            MockCustomer customer
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.LARGE_TRANSFER
        );
        transaction.setAmount(randomAmount(25_000, 180_000));
        transaction.setTransactionType("INTERNATIONAL_TRANSFER");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(pick(NORMAL_COUNTRIES));
        transaction.setRiskScore(randomScore(55, 78));
        transaction.setFlagged(transaction.getRiskScore()
                .compareTo(BigDecimal.valueOf(70)) >= 0);
        transaction.setRiskIndicators(new String[] {
                "LARGE_TRANSFER",
                "HIGH_VALUE_TRANSACTION"
        });
        transaction.setDescription("Large value transfer");
        return transaction;
    }

    private MockTransaction buildStructuringTransaction(
            MockCustomer customer
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.STRUCTURING
        );
        transaction.setAmount(randomAmount(8_800, 9_950));
        transaction.setTransactionType("CASH_DEPOSIT");
        transaction.setChannel("BRANCH");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(customer.getCountryOfResidence());
        transaction.setRiskScore(randomScore(78, 95));
        transaction.setFlagged(true);
        transaction.setRiskIndicators(new String[] {
                "STRUCTURING",
                "BELOW_REPORTING_THRESHOLD",
                "REPEATED_CASH_DEPOSITS"
        });
        transaction.setDescription("Branch cash deposit below reporting threshold");
        return transaction;
    }

    private MockTransaction buildRapidMovementTransaction(
            MockCustomer customer,
            int sequence
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.RAPID_MOVEMENT
        );
        transaction.setAmount(randomAmount(4_500, 12_000));
        transaction.setTransactionType("WIRE");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(pick(NORMAL_COUNTRIES));
        transaction.setRiskScore(randomScore(62, 88));
        transaction.setFlagged(true);
        transaction.setRiskIndicators(new String[] {
                "RAPID_MOVEMENT",
                "MULTIPLE_TRANSFERS"
        });
        transaction.setDescription(
                "Rapid outbound transfer sequence " + (sequence + 1)
        );
        transaction.setTransactionDate(
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusSeconds(Math.max(0, 4 - sequence))
        );
        return transaction;
    }

    private MockTransaction buildHighRiskCountryTransaction(
            MockCustomer customer
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.HIGH_RISK_COUNTRY
        );
        transaction.setAmount(randomAmount(6_000, 45_000));
        transaction.setTransactionType("INTERNATIONAL_TRANSFER");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(pick(HIGH_RISK_COUNTRIES));
        transaction.setCounterpartyCountry(transaction.getDestinationCountry());
        transaction.setRiskScore(randomScore(72, 96));
        transaction.setFlagged(true);
        transaction.setRiskIndicators(new String[] {
                "HIGH_RISK_COUNTRY",
                "CROSS_BORDER_TRANSFER"
        });
        transaction.setDescription("Cross-border transfer to high-risk country");
        return transaction;
    }

    private MockTransaction buildPepActivityTransaction(
            MockCustomer customer
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.PEP_ACTIVITY
        );
        transaction.setAmount(randomAmount(12_000, 65_000));
        transaction.setTransactionType("WIRE");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(pick(NORMAL_COUNTRIES));
        transaction.setRiskScore(randomScore(45, 72));
        transaction.setFlagged(
                transaction.getRiskScore().compareTo(BigDecimal.valueOf(65)) >= 0
        );
        transaction.setRiskIndicators(new String[] {
                "PEP_MONITORING",
                "ELEVATED_PUBLIC_OFFICE_ACTIVITY"
        });
        transaction.setDescription("PEP-linked account activity");
        return transaction;
    }

    private MockTransaction buildNewAccountActivityTransaction(
            MockCustomer customer
    ) {
        MockTransaction transaction = baseTransaction(
                customer,
                SimulationScenario.NEW_ACCOUNT_ACTIVITY
        );
        transaction.setAmount(randomAmount(3_500, 18_000));
        transaction.setTransactionType("TRANSFER");
        transaction.setChannel("MOBILE");
        transaction.setOriginCountry(customer.getCountryOfResidence());
        transaction.setDestinationCountry(customer.getCountryOfResidence());
        long accountAgeDays = ChronoUnit.DAYS.between(
                customer.getAccountOpened(),
                LocalDate.now(ZoneOffset.UTC)
        );
        transaction.setRiskScore(randomScore(35, 68));
        transaction.setFlagged(accountAgeDays <= 30);
        transaction.setRiskIndicators(new String[] {
                "NEW_ACCOUNT_ACTIVITY",
                "RECENT_ACCOUNT_OPENING"
        });
        transaction.setDescription("Recent account funding activity");
        return transaction;
    }

    private MockTransaction baseTransaction(
            MockCustomer customer,
            SimulationScenario scenario
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setCustomer(customer);
        transaction.setTransactionReference(nextReference());
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));
        transaction.setCurrency(currencyFor(customer));
        transaction.setTransactionStatus("COMPLETED");
        transaction.setCounterpartyName("Simulated Counterparty");
        transaction.setCounterpartyBank("Simulated Bank");
        transaction.setSimulationScenario(scenario.name());
        return transaction;
    }

    private String nextReference() {
        return "SIM-"
                + OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond()
                + "-"
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private BigDecimal randomAmount(int min, int max) {
        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal randomScore(int min, int max) {
        return BigDecimal.valueOf(
                ThreadLocalRandom.current().nextInt(min, max + 1)
        );
    }

    private String pick(List<String> values) {
        return values.get(
                ThreadLocalRandom.current().nextInt(values.size())
        );
    }

    private String currencyFor(MockCustomer customer) {
        return switch (customer.getCountryOfResidence()) {
            case "United Kingdom" -> "GBP";
            case "Canada" -> "CAD";
            case "Singapore" -> "SGD";
            case "United Arab Emirates" -> "AED";
            case "Mexico" -> "MXN";
            case "Nigeria" -> "NGN";
            case "India" -> "INR";
            default -> customer.getCountryOfResidence().equals("United States")
                    ? "USD"
                    : "EUR";
        };
    }

    record GeneratedTransaction(
            MockTransaction transaction,
            SimulationScenario scenario
    ) {
    }
}
