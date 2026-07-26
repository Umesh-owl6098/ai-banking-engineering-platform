package com.umeshowl.banking.notification;

import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
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
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Autowired
    private InvestigationCaseRepository investigationCaseRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MockCustomerRepository mockCustomerRepository;

    @Value("${investigation.auto-create.default-project-id}")
    private UUID defaultProjectId;

    private User supervisor;
    private User analyst;
    private InvestigationCase investigationCase;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        investigationCaseRepository.deleteAll();

        supervisor = createUser("supervisor", Role.SUPERVISOR);
        analyst = createUser("compliance.analyst", Role.COMPLIANCE_ANALYST);

        seedProject();
        MockCustomer customer = seedCustomer();

        investigationCase = new InvestigationCase();
        investigationCase.setProject(projectRepository.findById(defaultProjectId).orElseThrow());
        investigationCase.setCustomer(customer);
        investigationCase.setCaseType("FRAUD");
        investigationCase.setTitle("Notification test case");
        investigationCase.setDescription("Awaiting review");
        investigationCase.setStatus("AWAITING_REVIEW");
        investigationCase.setPriority("HIGH");
        investigationCase = investigationCaseRepository.save(investigationCase);
    }

    @Test
    void assignmentCreatesNotificationForAssignee() throws Exception {
        notificationPublisher.notifyAssigned(investigationCase, analyst, false);

        String token = jwtService.generateToken(analyst);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type")
                        .value("INVESTIGATION_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void unreadCountAndMarkRead() throws Exception {
        notificationPublisher.notifyEscalated(investigationCase, "compliance.analyst");

        String token = jwtService.generateToken(supervisor);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        String notificationId = notificationRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId()
                .toString();

        mockMvc.perform(post("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void markAllReadClearsUnreadCount() throws Exception {
        notificationPublisher.notifyReportGenerated(investigationCase.getId());

        String token = jwtService.generateToken(supervisor);

        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void usersOnlySeeOwnNotifications() throws Exception {
        notificationPublisher.notifyAssigned(investigationCase, analyst, false);

        String supervisorToken = jwtService.generateToken(supervisor);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
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
        customer.setFullName("Notification Test Customer");
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
