package com.umeshowl.banking.investigation;

import com.umeshowl.banking.investigation.report.InvestigationReportRepository;
import com.umeshowl.banking.notification.NotificationRepository;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import com.umeshowl.banking.project.ProjectStatus;
import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningResultRepository;
import com.umeshowl.banking.screening.TransactionScreeningService;
import com.umeshowl.banking.screening.TransactionScreeningStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("security-test")
class InvestigationCreationServiceTest {

    private static final UUID DEFAULT_PROJECT_ID =
            UUID.fromString("8c0c0dee-dd8e-4419-bef3-a2e93c10a726");

    @Autowired
    private InvestigationCreationService investigationCreationService;

    @Autowired
    private TransactionScreeningService screeningService;

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

    @Autowired
    private TransactionScreeningResultRepository screeningResultRepository;

    @Autowired
    private MockCustomerRepository customerRepository;

    @Autowired
    private MockTransactionRepository transactionRepository;

    @Autowired
    private ProjectRepository projectRepository;

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

        seedProject();
        customer = seedCustomer("Jordan Hayes", "NONE");
    }

    @Test
    void doesNothingForClearedTransaction() {
        MockTransaction transaction = persistTransaction(
                customer,
                "100.00",
                "10.00",
                false
        );
        TransactionScreeningResult screening =
                completeScreening(transaction);

        assertEquals(TransactionScreeningStatus.CLEARED, screening.getStatus());
        assertEquals(0, investigationCaseRepository.count());
    }

    @Test
    void createsInvestigationForSuspiciousTransaction() throws Exception {
        MockTransaction transaction = persistTransaction(
                customer,
                "15000.00",
                "45.00",
                false
        );
        TransactionScreeningResult screening =
                completeScreening(transaction);

        assertEquals(
                TransactionScreeningStatus.SUSPICIOUS,
                screening.getStatus()
        );
        assertEquals(1, investigationCaseRepository.count());

        InvestigationCase investigation =
                investigationCaseRepository.findAll().getFirst();
        assertTrue(investigation.isAutoCreated());
        assertEquals("HIGH", investigation.getPriority());
        assertEquals(
                TransactionScreeningStatus.SUSPICIOUS.name(),
                investigation.getScreeningStatus()
        );
        assertTrue(investigation.getScreeningTriggeredRules().length > 0);

        awaitInvestigationStatus(
                investigation.getId(),
                "AWAITING_REVIEW",
                Duration.ofSeconds(30)
        );
    }

    @Test
    void autoExecutesAfterCreationTransactionCommits() throws Exception {
        MockTransaction transaction = persistTransaction(
                customer,
                "15000.00",
                "45.00",
                false
        );
        completeScreening(transaction);

        InvestigationCase investigation =
                investigationCaseRepository.findAll().getFirst();

        awaitInvestigationStatus(
                investigation.getId(),
                "AWAITING_REVIEW",
                Duration.ofSeconds(30)
        );
    }

    @Test
    void createsInvestigationForCriticalTransaction() throws Exception {
        MockCustomer pepCustomer = seedCustomer(
                "Aleksandra Petrov",
                "PEP"
        );
        MockTransaction transaction = persistTransaction(
                pepCustomer,
                "25000.00",
                "85.00",
                true,
                "Iran",
                "United States"
        );

        TransactionScreeningResult screening =
                completeScreening(transaction);

        assertEquals(
                TransactionScreeningStatus.CRITICAL,
                screening.getStatus()
        );

        InvestigationCase investigation =
                investigationCaseRepository.findAll().getFirst();
        assertEquals("CRITICAL", investigation.getPriority());
        awaitInvestigationStatus(
                investigation.getId(),
                "AWAITING_REVIEW",
                Duration.ofSeconds(30)
        );
    }

    @Test
    void preventsDuplicateInvestigationsForSameTransaction() {
        MockTransaction transaction = persistTransaction(
                customer,
                "15000.00",
                "45.00",
                false
        );
        TransactionScreeningResult screening =
                completeScreening(transaction);

        assertTrue(
                investigationCreationService
                        .createIfRequired(screening)
                        .isEmpty()
        );
        assertEquals(1, investigationCaseRepository.count());
    }

    private TransactionScreeningResult completeScreening(
            MockTransaction transaction
    ) {
        TransactionScreeningResult processing =
                screeningService.beginProcessing(transaction);
        return screeningService.screen(processing);
    }

    private void awaitInvestigationStatus(
            UUID investigationId,
            String expectedStatus,
            Duration timeout
    ) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadlineNanos) {
            InvestigationCase investigation = investigationCaseRepository
                    .findById(investigationId)
                    .orElseThrow();

            if (expectedStatus.equals(investigation.getStatus())) {
                return;
            }

            Thread.sleep(50);
        }

        InvestigationCase investigation = investigationCaseRepository
                .findById(investigationId)
                .orElseThrow();
        fail(
                "Expected investigation status "
                        + expectedStatus
                        + " but was "
                        + investigation.getStatus()
        );
    }

    private void seedProject() {
        if (projectRepository.existsById(DEFAULT_PROJECT_ID)) {
            return;
        }

        Project project = new Project();
        project.setId(DEFAULT_PROJECT_ID);
        project.setName("Financial Crime Monitoring");
        project.setDescription("Test project");
        project.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(project);
    }

    private MockCustomer seedCustomer(String fullName, String pepStatus) {
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
        seeded.setAccountOpened(LocalDate.of(2018, 3, 15));
        return customerRepository.save(seeded);
    }

    private MockTransaction persistTransaction(
            MockCustomer owner,
            String amount,
            String riskScore,
            boolean flagged
    ) {
        return persistTransaction(
                owner,
                amount,
                riskScore,
                flagged,
                "United States",
                "United States"
        );
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
        transaction.setSimulationScenario("LARGE_TRANSFER");
        return transactionRepository.save(transaction);
    }
}
