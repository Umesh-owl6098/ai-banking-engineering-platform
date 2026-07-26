package com.umeshowl.banking.investigation.assignment;

import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.assignment.dto.AssignInvestigationRequest;
import com.umeshowl.banking.investigation.report.InvestigationReportRepository;
import com.umeshowl.banking.notification.NotificationRepository;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import com.umeshowl.banking.project.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class InvestigationAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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
    private MockCustomerRepository mockCustomerRepository;

    @Value("${investigation.auto-create.default-project-id}")
    private UUID defaultProjectId;

    private User supervisor;
    private User fraudAnalyst;
    private User complianceAnalyst;
    private InvestigationCase awaitingReviewCase;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        agentFindingCitationRepository.deleteAll();
        humanReviewDecisionRepository.deleteAll();
        investigationReportRepository.deleteAll();
        agentFindingRepository.deleteAll();
        investigationCaseEventRepository.deleteAll();
        notificationRepository.deleteAll();
        investigationCaseRepository.deleteAll();

        supervisor = createUser("supervisor", Role.SUPERVISOR);
        fraudAnalyst = createUser("fraud.analyst", Role.FRAUD_ANALYST);
        complianceAnalyst = createUser("compliance.analyst", Role.COMPLIANCE_ANALYST);

        seedProject();
        MockCustomer customer = seedCustomer();

        awaitingReviewCase = new InvestigationCase();
        awaitingReviewCase.setProject(
                projectRepository.findById(defaultProjectId).orElseThrow()
        );
        awaitingReviewCase.setCustomer(customer);
        awaitingReviewCase.setCaseType("FRAUD");
        awaitingReviewCase.setTitle("Assignment test case");
        awaitingReviewCase.setDescription("Awaiting analyst review");
        awaitingReviewCase.setStatus("AWAITING_REVIEW");
        awaitingReviewCase.setPriority("HIGH");
        awaitingReviewCase = investigationCaseRepository.save(awaitingReviewCase);
    }

    @Test
    void supervisorCanAssignInvestigation() throws Exception {
        String token = jwtService.generateToken(supervisor);

        mockMvc.perform(post("/api/investigations/{id}/assign", awaitingReviewCase.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeUsername": "compliance.analyst",
                                  "notes": "Priority review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAnalystUsername")
                        .value("compliance.analyst"));
    }

    @Test
    void analystCanClaimUnassignedInvestigation() throws Exception {
        String token = jwtService.generateToken(fraudAnalyst);

        mockMvc.perform(post("/api/investigations/{id}/claim", awaitingReviewCase.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAnalystUsername")
                        .value("fraud.analyst"));
    }

    @Test
    void duplicateClaimIsRejected() throws Exception {
        String supervisorToken = jwtService.generateToken(supervisor);
        String fraudToken = jwtService.generateToken(fraudAnalyst);

        mockMvc.perform(post("/api/investigations/{id}/assign", awaitingReviewCase.getId())
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeUsername":"compliance.analyst"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/investigations/{id}/claim", awaitingReviewCase.getId())
                        .header("Authorization", "Bearer " + fraudToken))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthorizedAssignmentIsRejected() throws Exception {
        String token = jwtService.generateToken(fraudAnalyst);

        mockMvc.perform(post("/api/investigations/{id}/assign", awaitingReviewCase.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeUsername":"compliance.analyst"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystQueueReturnsSections() throws Exception {
        String token = jwtService.generateToken(supervisor);

        mockMvc.perform(get("/api/analyst-queue")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", defaultProjectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassigned").isArray())
                .andExpect(jsonPath("$.myQueue").isArray())
                .andExpect(jsonPath("$.inReview").isArray())
                .andExpect(jsonPath("$.escalated").isArray());
    }

    private User createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void seedProject() {
        if (projectRepository.existsById(defaultProjectId)) {
            return;
        }

        Project project = new Project();
        project.setId(defaultProjectId);
        project.setName("Financial Crime Monitoring");
        project.setDescription("Test project");
        project.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(project);
    }

    private MockCustomer seedCustomer() {
        MockCustomer customer = new MockCustomer();
        customer.setFullName("Assignment Test Customer");
        customer.setDateOfBirth(LocalDate.of(1985, 1, 1));
        customer.setNationality("United States");
        customer.setCountryOfResidence("United States");
        customer.setAccountNumber(
                "ACC-" + UUID.randomUUID().toString().substring(0, 8)
        );
        customer.setAccountStatus("ACTIVE");
        customer.setKycStatus("VERIFIED");
        customer.setRiskRating("MEDIUM");
        customer.setPepStatus("NONE");
        customer.setAccountOpened(LocalDate.of(2018, 3, 15));
        return mockCustomerRepository.save(customer);
    }
}
