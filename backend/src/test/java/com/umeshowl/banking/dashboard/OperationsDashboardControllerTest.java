package com.umeshowl.banking.dashboard;

import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class OperationsDashboardControllerTest {

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

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    @Test
    void operationsDashboardReturnsAggregatedMetrics() throws Exception {
        String token = jwtService.generateToken(
                userRepository.findByUsername("admin").orElseThrow()
        );

        mockMvc.perform(get("/api/dashboard/operations")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis").exists())
                .andExpect(jsonPath("$.investigationsByStatus").isArray())
                .andExpect(jsonPath("$.agentActivity").isArray())
                .andExpect(jsonPath("$.criticalAlerts").isArray())
                .andExpect(jsonPath("$.recentTransactions").isArray())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void operationsDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/operations"))
                .andExpect(status().isUnauthorized());
    }
}
