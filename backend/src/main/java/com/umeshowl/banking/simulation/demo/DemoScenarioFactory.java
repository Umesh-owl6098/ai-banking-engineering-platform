package com.umeshowl.banking.simulation.demo;

import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DemoScenarioFactory {

    private static final String AVA_MITCHELL_ACCOUNT = "ACC-100001";
    private static final String MARCUS_REED_ACCOUNT = "ACC-100013";
    private static final String LEILA_FAROUK_ACCOUNT = "ACC-100014";
    private static final String JORDAN_HAYES_ACCOUNT = "ACC-100015";
    private static final String ALEKSANDRA_PETROV_ACCOUNT = "ACC-100016";

    private static final BigDecimal STRUCTURING_DEPOSIT_AMOUNT =
            new BigDecimal("9800.00");
    private static final BigDecimal HIGH_RISK_WIRE_AMOUNT =
            new BigDecimal("85000.00");
    private static final BigDecimal MONEY_MULE_INBOUND_AMOUNT =
            new BigDecimal("8000.00");
    private static final BigDecimal MONEY_MULE_OUTBOUND_AMOUNT =
            new BigDecimal("22000.00");
    private static final BigDecimal NORMAL_ACTIVITY_AMOUNT =
            new BigDecimal("450.00");

    private final MockCustomerRepository customerRepository;
    private final MockTransactionRepository transactionRepository;
    private final AtomicInteger executionCounter = new AtomicInteger();

    public DemoScenarioFactory(
            MockCustomerRepository customerRepository,
            MockTransactionRepository transactionRepository
    ) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    public DemoScenarioBatch generate(DemoScenarioType scenarioType) {
        String scenarioGroupId = nextScenarioGroupId(scenarioType);

        List<MockTransaction> transactions = switch (scenarioType) {
            case STRUCTURING_DEMO -> buildStructuringDemo(scenarioGroupId);
            case HIGH_RISK_WIRE_DEMO -> buildHighRiskWireDemo(scenarioGroupId);
            case MONEY_MULE_DEMO -> buildMoneyMuleDemo(scenarioGroupId);
            case NORMAL_ACTIVITY_DEMO -> buildNormalActivityDemo(scenarioGroupId);
        };

        List<MockTransaction> saved = transactionRepository.saveAll(transactions);
        return new DemoScenarioBatch(scenarioType, scenarioGroupId, saved);
    }

    private List<MockTransaction> buildStructuringDemo(String scenarioGroupId) {
        MockCustomer customer = requireCustomer(MARCUS_REED_ACCOUNT);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        List<MockTransaction> transactions = new ArrayList<>(6);

        for (int index = 0; index < 6; index++) {
            MockTransaction transaction = baseTransaction(
                    customer,
                    scenarioGroupId,
                    DemoScenarioType.STRUCTURING_DEMO,
                    reference(scenarioGroupId, "CASH", index + 1)
            );
            transaction.setAmount(STRUCTURING_DEPOSIT_AMOUNT);
            transaction.setCurrency("USD");
            transaction.setTransactionType("CASH_DEPOSIT");
            transaction.setChannel("BRANCH");
            transaction.setOriginCountry("United States");
            transaction.setDestinationCountry("United States");
            transaction.setCounterpartyName("Reed Corner Store");
            transaction.setCounterpartyBank("First National Bank");
            transaction.setCounterpartyCountry("United States");
            transaction.setRiskScore(new BigDecimal("85.00"));
            transaction.setFlagged(true);
            transaction.setRiskIndicators(new String[] {
                    "STRUCTURING_DEMO",
                    "BELOW_REPORTING_THRESHOLD",
                    "REPEATED_CASH_DEPOSITS"
            });
            transaction.setDescription(
                    "Demo structuring cash deposit "
                            + (index + 1)
                            + " of 6"
            );
            transaction.setTransactionDate(
                    baseTime.minusMinutes(55L - (index * 10L))
            );
            transactions.add(transaction);
        }

        return transactions;
    }

    private List<MockTransaction> buildHighRiskWireDemo(String scenarioGroupId) {
        MockCustomer customer = requireCustomer(ALEKSANDRA_PETROV_ACCOUNT);
        MockTransaction transaction = baseTransaction(
                customer,
                scenarioGroupId,
                DemoScenarioType.HIGH_RISK_WIRE_DEMO,
                reference(scenarioGroupId, "WIRE", 1)
        );
        transaction.setAmount(HIGH_RISK_WIRE_AMOUNT);
        transaction.setCurrency("GBP");
        transaction.setTransactionType("INTERNATIONAL_TRANSFER");
        transaction.setChannel("BRANCH");
        transaction.setOriginCountry("United Kingdom");
        transaction.setDestinationCountry("Syria");
        transaction.setCounterpartyName("Damascus Trade Exchange");
        transaction.setCounterpartyBank("Middle East Commerce Bank");
        transaction.setCounterpartyCountry("Syria");
        transaction.setRiskScore(new BigDecimal("92.00"));
        transaction.setFlagged(true);
        transaction.setRiskIndicators(new String[] {
                "HIGH_RISK_WIRE_DEMO",
                "LARGE_TRANSFER",
                "HIGH_RISK_COUNTRY",
                "PROFILE_MISMATCH"
        });
        transaction.setDescription(
                "Demo high-risk international wire to sanctioned-adjacent country"
        );
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));

        return List.of(transaction);
    }

    private List<MockTransaction> buildMoneyMuleDemo(String scenarioGroupId) {
        MockCustomer customer = requireCustomer(JORDAN_HAYES_ACCOUNT);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        List<MockTransaction> transactions = new ArrayList<>(4);

        for (int index = 0; index < 3; index++) {
            MockTransaction inbound = baseTransaction(
                    customer,
                    scenarioGroupId,
                    DemoScenarioType.MONEY_MULE_DEMO,
                    reference(scenarioGroupId, "IN", index + 1)
            );
            inbound.setAmount(MONEY_MULE_INBOUND_AMOUNT);
            inbound.setCurrency("USD");
            inbound.setTransactionType("ACH");
            inbound.setChannel("MOBILE");
            inbound.setOriginCountry("United States");
            inbound.setDestinationCountry("United States");
            inbound.setCounterpartyName("Remote Payroll Services");
            inbound.setCounterpartyBank("Regional ACH Network");
            inbound.setCounterpartyCountry("United States");
            inbound.setRiskScore(new BigDecimal("58.00"));
            inbound.setFlagged(index == 0);
            inbound.setRiskIndicators(new String[] {
                    "MONEY_MULE_DEMO",
                    "NEW_ACCOUNT_ACTIVITY",
                    "INBOUND_DEPOSIT"
            });
            inbound.setDescription(
                    "Demo inbound mule deposit " + (index + 1) + " of 3"
            );
            inbound.setTransactionDate(
                    baseTime.minusHours(3).plusMinutes(index * 20L)
            );
            transactions.add(inbound);
        }

        MockTransaction outbound = baseTransaction(
                customer,
                scenarioGroupId,
                DemoScenarioType.MONEY_MULE_DEMO,
                reference(scenarioGroupId, "OUT", 4)
        );
        outbound.setAmount(MONEY_MULE_OUTBOUND_AMOUNT);
        outbound.setCurrency("USD");
        outbound.setTransactionType("INTERNATIONAL_TRANSFER");
        outbound.setChannel("ONLINE");
        outbound.setOriginCountry("United States");
        outbound.setDestinationCountry("Iran");
        outbound.setCounterpartyName("Tehran Commodity Brokers");
        outbound.setCounterpartyBank("Persian Gulf Bank");
        outbound.setCounterpartyCountry("Iran");
        outbound.setRiskScore(new BigDecimal("88.00"));
        outbound.setFlagged(true);
        outbound.setRiskIndicators(new String[] {
                "MONEY_MULE_DEMO",
                "RAPID_MOVEMENT",
                "HIGH_RISK_COUNTRY"
        });
        outbound.setDescription(
                "Demo rapid outbound transfer of aggregated inbound funds"
        );
        outbound.setTransactionDate(baseTime.minusMinutes(15));
        transactions.add(outbound);

        return transactions;
    }

    private List<MockTransaction> buildNormalActivityDemo(String scenarioGroupId) {
        MockCustomer customer = requireCustomer(AVA_MITCHELL_ACCOUNT);
        MockTransaction transaction = baseTransaction(
                customer,
                scenarioGroupId,
                DemoScenarioType.NORMAL_ACTIVITY_DEMO,
                reference(scenarioGroupId, "NORM", 1)
        );
        transaction.setAmount(NORMAL_ACTIVITY_AMOUNT);
        transaction.setCurrency("USD");
        transaction.setTransactionType("ACH");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry("United States");
        transaction.setDestinationCountry("United States");
        transaction.setCounterpartyName("City Utilities");
        transaction.setCounterpartyBank("First National Bank");
        transaction.setCounterpartyCountry("United States");
        transaction.setRiskScore(new BigDecimal("8.00"));
        transaction.setFlagged(false);
        transaction.setRiskIndicators(new String[] { "ROUTINE_ACTIVITY" });
        transaction.setDescription("Demo routine domestic utility payment");
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));

        return List.of(transaction);
    }

    private MockTransaction baseTransaction(
            MockCustomer customer,
            String scenarioGroupId,
            DemoScenarioType scenarioType,
            String reference
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setCustomer(customer);
        transaction.setTransactionReference(reference);
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));
        transaction.setTransactionStatus("COMPLETED");
        transaction.setSimulationScenario(scenarioType.name());
        transaction.setScenarioGroupId(scenarioGroupId);
        return transaction;
    }

    private MockCustomer requireCustomer(String accountNumber) {
        return customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Required demo customer not found: "
                                        + accountNumber
                        )
                );
    }

    private String nextScenarioGroupId(DemoScenarioType scenarioType) {
        return "demo-"
                + scenarioType.name().toLowerCase().replace('_', '-')
                + "-"
                + OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond()
                + "-"
                + executionCounter.incrementAndGet();
    }

    private String reference(
            String scenarioGroupId,
            String prefix,
            int sequence
    ) {
        String suffix = scenarioGroupId.substring(
                Math.max(0, scenarioGroupId.length() - 8)
        );
        return "DEMO-"
                + prefix
                + "-"
                + suffix
                + "-"
                + sequence;
    }

    public record DemoScenarioBatch(
            DemoScenarioType scenarioType,
            String scenarioGroupId,
            List<MockTransaction> transactions
    ) {
    }
}
