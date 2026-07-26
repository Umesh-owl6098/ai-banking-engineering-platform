package com.umeshowl.banking.screening;

import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.report.InvestigationReportRepository;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import com.umeshowl.banking.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("security-test")
class TransactionScreeningServiceTest {

    @Autowired
    private TransactionScreeningService screeningService;

    @Autowired
    private MockCustomerRepository customerRepository;

    @Autowired
    private MockTransactionRepository transactionRepository;

    @Autowired
    private TransactionScreeningResultRepository screeningResultRepository;

    @Autowired
    private InvestigationCaseRepository investigationCaseRepository;

    @Autowired
    private AgentFindingCitationRepository agentFindingCitationRepository;

    @Autowired
    private HumanReviewDecisionRepository humanReviewDecisionRepository;

    @Autowired
    private AgentFindingRepository agentFindingRepository;

    @Autowired
    private InvestigationReportRepository investigationReportRepository;

    @Autowired
    private InvestigationCaseEventRepository investigationCaseEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private MockCustomer customer;

    @BeforeEach
    void setUp() {
        agentFindingCitationRepository.deleteAll();
        humanReviewDecisionRepository.deleteAll();
        investigationReportRepository.deleteAll();
        agentFindingRepository.deleteAll();
        investigationCaseEventRepository.deleteAll();
        notificationRepository.deleteAll();
        investigationCaseRepository.deleteAll();
        screeningResultRepository.deleteAll();
        transactionRepository.deleteAll();
        customerRepository.deleteAll();

        customer = seedCustomer(
                "Jordan Hayes",
                "NONE",
                LocalDate.of(2018, 3, 15)
        );
    }

    @Test
    void clearsNormalTransaction() {
        MockTransaction transaction = persistTransaction(
                customer,
                "150.00",
                "12.00",
                false,
                "United States",
                "United States"
        );

        TransactionScreeningResult result = completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.CLEARED, result.getStatus());
        assertEquals(0, result.getTriggeredRules().length);
        assertEquals("No screening rules triggered", result.getReason());
    }

    @Test
    void flagsLargeTransferAsSuspicious() {
        MockTransaction transaction = persistTransaction(
                customer,
                "15000.00",
                "45.00",
                false,
                "United States",
                "United States"
        );

        TransactionScreeningResult result = completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.SUSPICIOUS, result.getStatus());
        assertTrue(containsRule(result, TransactionScreeningRule.LARGE_TRANSFER));
    }

    @Test
    void flagsStructuringPattern() {
        persistTransaction(
                customer,
                "9800.00",
                "78.00",
                true,
                "United States",
                "United States"
        );
        MockTransaction current = persistTransaction(
                customer,
                "9900.00",
                "82.00",
                true,
                "United States",
                "United States"
        );

        TransactionScreeningResult result = completeScreening(current);

        assertEquals(TransactionScreeningStatus.SUSPICIOUS, result.getStatus());
        assertTrue(containsRule(result, TransactionScreeningRule.STRUCTURING));
        assertTrue(containsRule(result, TransactionScreeningRule.FLAGGED_STATUS));
    }

    @Test
    void flagsRapidMovementPattern() {
        persistTransaction(
                customer,
                "12000.00",
                "55.00",
                false,
                "United States",
                "Canada"
        );
        MockTransaction current = persistTransaction(
                customer,
                "11000.00",
                "60.00",
                false,
                "United States",
                "Canada"
        );

        TransactionScreeningResult result = completeScreening(current);

        assertEquals(TransactionScreeningStatus.SUSPICIOUS, result.getStatus());
        assertTrue(containsRule(result, TransactionScreeningRule.RAPID_MOVEMENT));
    }

    @Test
    void marksCriticalWhenScoreReachesThreshold() {
        MockCustomer pepCustomer = seedCustomer(
                "Aleksandra Petrov",
                "PEP",
                LocalDate.of(2010, 1, 1)
        );
        MockTransaction transaction = persistTransaction(
                pepCustomer,
                "25000.00",
                "85.00",
                true,
                "Iran",
                "United States"
        );

        TransactionScreeningResult result = completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.CRITICAL, result.getStatus());
        assertTrue(result.getScreeningScore().intValue() >= 80);
    }

    @Test
    void flagsHighRiskCountryAndPepActivity() {
        MockCustomer pepCustomer = seedCustomer(
                "Aleksandra Petrov",
                "PEP",
                LocalDate.of(2010, 1, 1)
        );
        MockTransaction transaction = persistTransaction(
                pepCustomer,
                "5000.00",
                "70.00",
                false,
                "United States",
                "Iran"
        );

        TransactionScreeningResult result = completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.SUSPICIOUS, result.getStatus());
        assertTrue(containsRule(result, TransactionScreeningRule.HIGH_RISK_COUNTRY));
        assertTrue(containsRule(result, TransactionScreeningRule.PEP_ACTIVITY));
    }

    @Test
    void flagsNewAccountActivity() {
        MockCustomer newCustomer = seedCustomer(
                "Taylor Brooks",
                "NONE",
                LocalDate.now(ZoneOffset.UTC).minusDays(30)
        );
        MockTransaction transaction = persistTransaction(
                newCustomer,
                "2500.00",
                "20.00",
                false,
                "United States",
                "United States"
        );

        TransactionScreeningResult result = completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.SUSPICIOUS, result.getStatus());
        assertTrue(containsRule(
                result,
                TransactionScreeningRule.NEW_ACCOUNT_ACTIVITY
        ));
    }

    @Test
    void beginsWithProcessingStatus() {
        MockTransaction transaction = persistTransaction(
                customer,
                "100.00",
                "5.00",
                false,
                "United States",
                "United States"
        );

        TransactionScreeningResult processing =
                screeningService.beginProcessing(transaction);

        assertEquals(
                TransactionScreeningStatus.PROCESSING,
                processing.getStatus()
        );
    }

    private TransactionScreeningResult completeScreening(
            MockTransaction transaction
    ) {
        TransactionScreeningResult processing =
                screeningService.beginProcessing(transaction);
        return screeningService.screen(processing);
    }

    private boolean containsRule(
            TransactionScreeningResult result,
            TransactionScreeningRule rule
    ) {
        for (String triggeredRule : result.getTriggeredRules()) {
            if (rule.name().equals(triggeredRule)) {
                return true;
            }
        }
        return false;
    }

    private MockCustomer seedCustomer(
            String fullName,
            String pepStatus,
            LocalDate accountOpened
    ) {
        MockCustomer seeded = new MockCustomer();
        seeded.setFullName(fullName);
        seeded.setDateOfBirth(LocalDate.of(1985, 1, 1));
        seeded.setNationality("United States");
        seeded.setCountryOfResidence("United States");
        seeded.setAccountNumber("ACC-" + UUID.randomUUID().toString().substring(0, 8));
        seeded.setAccountStatus("ACTIVE");
        seeded.setKycStatus("VERIFIED");
        seeded.setRiskRating("MEDIUM");
        seeded.setPepStatus(pepStatus);
        seeded.setAccountOpened(accountOpened);
        return customerRepository.save(seeded);
    }

    private MockTransaction persistTransaction(
            MockCustomer owner,
            String amount,
            String riskScore,
            boolean flagged,
            String originCountry,
            String destinationCountry
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setCustomer(owner);
        transaction.setTransactionReference(
                "SIM-" + UUID.randomUUID().toString().substring(0, 8)
        );
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("USD");
        transaction.setTransactionType("TRANSFER");
        transaction.setTransactionStatus("COMPLETED");
        transaction.setChannel("ONLINE");
        transaction.setOriginCountry(originCountry);
        transaction.setDestinationCountry(destinationCountry);
        transaction.setFlagged(flagged);
        transaction.setRiskScore(new BigDecimal(riskScore));
        transaction.setSimulationScenario("NORMAL");
        return transactionRepository.save(transaction);
    }
}
