package com.umeshowl.banking.simulation;

import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.report.InvestigationReportRepository;
import com.umeshowl.banking.notification.NotificationRepository;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import com.umeshowl.banking.project.ProjectStatus;
import com.umeshowl.banking.screening.TransactionScreeningResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class TransactionSimulationIntegrationTest {

    private static final UUID DEFAULT_PROJECT_ID =
            UUID.fromString("8c0c0dee-dd8e-4419-bef3-a2e93c10a726");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TransactionSimulationService simulationService;

    @BeforeEach
    void setUp() {
        if (simulationService.status().running()) {
            simulationService.stop();
        }
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
        userRepository.deleteAll();

        seedProject();

        seedCustomer("Ava Mitchell", "NONE", LocalDate.of(2018, 6, 12));
        seedCustomer("Aleksandra Petrov", "PEP", LocalDate.of(2012, 4, 18));
        seedCustomer("Jordan Hayes", "NONE", LocalDate.of(2025, 10, 3));

        seedUser("admin", Role.ADMIN);
        seedUser("readonly", Role.READ_ONLY);
    }

    @Test
    void startsAndStopsSimulation() throws Exception {
        mockMvc.perform(post("/api/simulation/start")
                        .header("Authorization", bearerToken("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"NORMAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.scenario").value("NORMAL"));

        mockMvc.perform(post("/api/simulation/start")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/simulation/stop")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    void generatesScenarioBatch() throws Exception {
        mockMvc.perform(post("/api/simulation/scenario/STRUCTURING")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("STRUCTURING"));

        assertEquals(1, transactionRepository.count());
    }

    @Test
    void generatesScenarioBatchWithScreeningResult() throws Exception {
        mockMvc.perform(post("/api/simulation/scenario/STRUCTURING")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("STRUCTURING"));

        assertEquals(1, screeningResultRepository.count());
        assertEquals(1, investigationCaseRepository.count());

        var screening = screeningResultRepository.findAll().getFirst();
        var investigation = investigationCaseRepository.findAll().getFirst();
        assertTrue(
                screening.getStatus() == com.umeshowl.banking.screening.TransactionScreeningStatus.SUSPICIOUS
                        || screening.getStatus()
                                == com.umeshowl.banking.screening.TransactionScreeningStatus.CRITICAL
        );
        assertTrue(screening.getTriggeredRules().length > 0);
        assertTrue(investigation.isAutoCreated());
        awaitInvestigationStatus(
                investigation.getId(),
                "AWAITING_REVIEW",
                Duration.ofSeconds(30)
        );
        assertEquals(transactionRepository.findAll().getFirst().getId(), investigation.getTransaction().getId());
    }

    private void awaitInvestigationStatus(
            UUID investigationId,
            String expectedStatus,
            Duration timeout
    ) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadlineNanos) {
            InvestigationCase current = investigationCaseRepository
                    .findById(investigationId)
                    .orElseThrow();

            if (expectedStatus.equals(current.getStatus())) {
                return;
            }

            Thread.sleep(50);
        }

        InvestigationCase current = investigationCaseRepository
                .findById(investigationId)
                .orElseThrow();
        fail(
                "Expected investigation status "
                        + expectedStatus
                        + " but was "
                        + current.getStatus()
        );
    }

    @Test
    void generatesStructuringTransaction() throws Exception {
        simulationService.generateScenarioBatch(SimulationScenario.STRUCTURING);

        var transaction = transactionRepository.findAll().getFirst();
        assertEquals("STRUCTURING", transaction.getSimulationScenario());
        assertTrue(transaction.isFlagged());
        assertTrue(transaction.getAmount().compareTo(
                java.math.BigDecimal.valueOf(10_000)
        ) < 0);
    }

    @Test
    void generatesRapidMovementBatch() throws Exception {
        simulationService.generateScenarioBatch(SimulationScenario.RAPID_MOVEMENT);

        assertTrue(transactionRepository.count() >= 3);
        transactionRepository.findAll().forEach(transaction ->
                assertEquals(
                        "RAPID_MOVEMENT",
                        transaction.getSimulationScenario()
                )
        );
    }

    @Test
    void publishesTransactionsToStreamEndpoint() throws Exception {
        mockMvc.perform(get("/api/simulation/live")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isOk());

        simulationService.generateScenarioBatch(SimulationScenario.NORMAL);
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void readonlyCannotStartSimulation() throws Exception {
        mockMvc.perform(post("/api/simulation/start")
                        .header("Authorization", bearerToken("readonly"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"NORMAL"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUsersCanReadStatusAndLiveStream() throws Exception {
        mockMvc.perform(get("/api/simulation/status")
                        .header("Authorization", bearerToken("readonly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        mockMvc.perform(get("/api/simulation/live")
                        .header("Authorization", bearerToken("readonly")))
                .andExpect(status().isOk());
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

    private void seedUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private void seedCustomer(
            String fullName,
            String pepStatus,
            LocalDate accountOpened
    ) {
        MockCustomer customer = new MockCustomer();
        customer.setFullName(fullName);
        customer.setDateOfBirth(LocalDate.of(1985, 1, 1));
        customer.setNationality("United States");
        customer.setCountryOfResidence("United States");
        customer.setAccountNumber("ACC-" + UUID.randomUUID().toString().substring(0, 8));
        customer.setAccountStatus("ACTIVE");
        customer.setKycStatus("VERIFIED");
        customer.setRiskRating("MEDIUM");
        customer.setPepStatus(pepStatus);
        customer.setAccountOpened(accountOpened);
        customerRepository.save(customer);
    }

    private String bearerToken(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return "Bearer " + jwtService.generateToken(user);
    }
}
