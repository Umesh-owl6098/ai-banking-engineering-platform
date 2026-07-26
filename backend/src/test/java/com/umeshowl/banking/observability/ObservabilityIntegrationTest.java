package com.umeshowl.banking.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.auth.dto.LoginRequest;
import com.umeshowl.banking.auth.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class ObservabilityIntegrationTest {

    private static final String CORRELATION_HEADER =
            ObservabilityConstants.CORRELATION_ID_HEADER;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        seedUser("admin", Role.ADMIN);
        seedUser("readonly", Role.READ_ONLY);
    }

    @Test
    void healthEndpointIsPublicAndReportsCustomIndicators() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.ragKnowledge").exists())
                .andExpect(jsonPath("$.components.openAiConfiguration").exists())
                .andExpect(jsonPath("$.components.investigationExecution").exists());
    }

    @Test
    void prometheusRequiresAdminOrSupervisor() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", bearerToken("readonly")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", bearerToken("admin")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Type"));
    }

    @Test
    void correlationIdIsGeneratedAndReturned() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_HEADER));
    }

    @Test
    void correlationIdAcceptsValidClientValue() throws Exception {
        String correlationId = "client-correlation-12345678";

        mockMvc.perform(get("/actuator/health")
                        .header(CORRELATION_HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CORRELATION_HEADER, correlationId));
    }

    @Test
    void loginIncrementsAuthenticationMetrics() throws Exception {
        double successBefore = counterTotal("authentication.success.total");
        double failureBefore = counterTotal("authentication.failure.total");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin", "Password123!")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin", "wrong-password")
                        )))
                .andExpect(status().isUnauthorized());

        assertTrue(counterTotal("authentication.success.total") > successBefore);
        assertTrue(counterTotal("authentication.failure.total") > failureBefore);
    }

    @Test
    void authorizationDeniedIncrementsMetric() throws Exception {
        double deniedBefore = counterTotal("authorization.denied.total");

        mockMvc.perform(post("/api/investigations")
                        .header("Authorization", bearerToken("readonly"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":"8c0c0dee-dd8e-4419-bef3-a2e93c10a726",
                                  "caseType":"FRAUD",
                                  "title":"Blocked",
                                  "description":"Should fail",
                                  "priority":"HIGH"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertTrue(
                counterTotal("authorization.denied.total") > deniedBefore
        );
    }

    private void seedUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String bearerToken(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return "Bearer " + jwtService.generateToken(user);
    }

    private double counterTotal(String name) {
        var counter = meterRegistry.find(name).counter();
        assertNotNull(counter);
        return counter.count();
    }
}
