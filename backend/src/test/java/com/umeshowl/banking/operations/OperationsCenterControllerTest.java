package com.umeshowl.banking.operations;

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
class OperationsCenterControllerTest {

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
    void operationsCenterReturnsAggregatedSnapshot() throws Exception {
        String token = jwtService.generateToken(
                userRepository.findByUsername("admin").orElseThrow()
        );

        mockMvc.perform(get("/api/operations/center")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformHealth.overallStatus").exists())
                .andExpect(jsonPath("$.platformHealth.components").isArray())
                .andExpect(jsonPath("$.investigationMetrics").exists())
                .andExpect(jsonPath("$.agentPerformance").isArray())
                .andExpect(jsonPath("$.recentErrors").isArray())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void operationsCenterRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/operations/center"))
                .andExpect(status().isUnauthorized());
    }
}
