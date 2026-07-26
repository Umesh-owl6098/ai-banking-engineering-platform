package com.umeshowl.banking.simulation.demo;

import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class DemoScenarioIntegrationTest {

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
    private MockTransactionRepository transactionRepository;

    @Autowired
    private MockCustomerRepository customerRepository;

    @Autowired
    private TransactionScreeningResultRepository screeningResultRepository;

    @Autowired
    private InvestigationCaseRepository investigationCaseRepository;

    @Autowired
    private AgentFindingCitationRepository agentFindingCitationRepository;

    @Autowired
    private AgentFindingRepository agentFindingRepository;

    @Autowired
    private HumanReviewDecisionRepository humanReviewDecisionRepository;

    @Autowired
    private InvestigationReportRepository investigationReportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        agentFindingCitationRepository.deleteAll();
        humanReviewDecisionRepository.deleteAll();
        agentFindingRepository.deleteAll();
        investigationReportRepository.deleteAll();
        notificationRepository.deleteAll();
        investigationCaseRepository.deleteAll();
        screeningResultRepository.deleteAll();
        transactionRepository.deleteAll();
        customerRepository.deleteAll();

        seedProject();
        seedDemoCustomers();

        userRepository.deleteAll();
        User admin = new User();
        admin.setUsername("admin.demo");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);
    }

    @Test
    void structuringDemoCreatesOneInvestigationForGroup() throws Exception {
        mockMvc.perform(post("/api/simulation/demos/structuring")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("STRUCTURING_DEMO"))
                .andExpect(jsonPath("$.transactionsGenerated").value(6))
                .andExpect(jsonPath("$.investigationId").isNotEmpty())
                .andExpect(jsonPath("$.scenarioGroupId").isNotEmpty());

        long investigations = investigationCaseRepository.count();
        assertEquals(1, investigations);

        var investigation = investigationCaseRepository.findAll().getFirst();
        assertNotNull(investigation.getScenarioGroupId());
        assertTrue(investigation.getScreeningTriggeredRules().length > 0);
    }

    @Test
    void normalActivityDemoCreatesNoInvestigation() throws Exception {
        mockMvc.perform(post("/api/simulation/demos/normal")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("NORMAL_ACTIVITY_DEMO"))
                .andExpect(jsonPath("$.transactionsGenerated").value(1))
                .andExpect(jsonPath("$.investigationId").isEmpty());

        assertEquals(0, investigationCaseRepository.count());
    }

    @Test
    void highRiskWireDemoCreatesCriticalInvestigation() throws Exception {
        mockMvc.perform(post("/api/simulation/demos/high-risk-wire")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("HIGH_RISK_WIRE_DEMO"))
                .andExpect(jsonPath("$.investigationId").isNotEmpty())
                .andExpect(jsonPath("$.transactions[0].screeningStatus")
                        .value("CRITICAL"));

        var investigation = investigationCaseRepository.findAll().getFirst();
        assertEquals("CRITICAL", investigation.getScreeningStatus());
    }

    @Test
    void repeatExecutionGeneratesUniqueGroupIds() throws Exception {
        String firstGroup = mockMvc.perform(post("/api/simulation/demos/normal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondGroup = mockMvc.perform(post("/api/simulation/demos/normal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(firstGroup.contains("scenarioGroupId"));
        assertTrue(secondGroup.contains("scenarioGroupId"));
        assertTrue(
                transactionRepository.findAll().stream()
                        .map(tx -> tx.getScenarioGroupId())
                        .distinct()
                        .count() >= 2
        );
    }

    private void seedProject() {
        if (projectRepository.existsById(DEFAULT_PROJECT_ID)) {
            return;
        }

        Project project = new Project();
        project.setId(DEFAULT_PROJECT_ID);
        project.setName("Demo Project");
        project.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(project);
    }

    private void seedDemoCustomers() {
        seedCustomer(
                "Ava Mitchell",
                "ACC-100001",
                "LOW",
                "NONE",
                LocalDate.of(2018, 6, 12),
                "United States"
        );
        seedCustomer(
                "Marcus Reed",
                "ACC-100013",
                "HIGH",
                "NONE",
                LocalDate.of(2019, 9, 14),
                "United States"
        );
        seedCustomer(
                "Leila Farouk",
                "ACC-100014",
                "HIGH",
                "NONE",
                LocalDate.of(2017, 2, 27),
                "United Kingdom"
        );
        seedCustomer(
                "Jordan Hayes",
                "ACC-100015",
                "HIGH",
                "NONE",
                LocalDate.of(2025, 10, 3),
                "United States"
        );
        seedCustomer(
                "Aleksandra Petrov",
                "ACC-100016",
                "HIGH",
                "PEP",
                LocalDate.of(2012, 4, 18),
                "United Kingdom"
        );
    }

    private void seedCustomer(
            String fullName,
            String accountNumber,
            String riskRating,
            String pepStatus,
            LocalDate accountOpened,
            String countryOfResidence
    ) {
        if (customerRepository.findByAccountNumber(accountNumber).isPresent()) {
            return;
        }

        MockCustomer customer = new MockCustomer();
        customer.setFullName(fullName);
        customer.setDateOfBirth(LocalDate.of(1985, 1, 1));
        customer.setNationality(countryOfResidence);
        customer.setCountryOfResidence(countryOfResidence);
        customer.setAccountNumber(accountNumber);
        customer.setAccountStatus("ACTIVE");
        customer.setEmail(fullName.toLowerCase().replace(' ', '.') + "@example.test");
        customer.setOccupation("Demo customer");
        customer.setSourceOfFunds("Employment income");
        customer.setKycStatus("VERIFIED");
        customer.setRiskRating(riskRating);
        customer.setPepStatus(pepStatus);
        customer.setAccountOpened(accountOpened);
        customerRepository.save(customer);
    }
}
