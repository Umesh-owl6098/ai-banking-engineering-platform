package com.umeshowl.banking.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.auth.dto.LoginRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class SecurityAuthorizationTest {

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

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        seedUser("admin", Role.ADMIN);
        seedUser("supervisor", Role.SUPERVISOR);
        seedUser("fraud.analyst", Role.FRAUD_ANALYST);
        seedUser("compliance.analyst", Role.COMPLIANCE_ANALYST);
        seedUser("readonly", Role.READ_ONLY);
    }

    @Test
    void loginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin", "Password123!")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void loginFailsWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin", "wrong-password")
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyCannotCreateInvestigation() throws Exception {
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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void fraudAnalystCanCreateInvestigation() throws Exception {
        mockMvc.perform(post("/api/investigations")
                        .header("Authorization", bearerToken("fraud.analyst"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":"8c0c0dee-dd8e-4419-bef3-a2e93c10a726",
                                  "caseType":"FRAUD",
                                  "title":"Allowed",
                                  "description":"Should pass auth",
                                  "priority":"HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readOnlyCanReadMockCustomers() throws Exception {
        mockMvc.perform(get("/api/mock/customers")
                        .header("Authorization", bearerToken("readonly")))
                .andExpect(status().isOk());
    }

    @Test
    void fraudAnalystCannotExecuteInvestigation() throws Exception {
        UUID investigationId = UUID.randomUUID();
        mockMvc.perform(post("/api/investigations/" + investigationId + "/execute")
                        .header("Authorization", bearerToken("fraud.analyst")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanExecuteInvestigation() throws Exception {
        UUID investigationId = UUID.randomUUID();
        mockMvc.perform(post("/api/investigations/" + investigationId + "/execute")
                        .header("Authorization", bearerToken("supervisor")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fraudAnalystCannotApproveDecision() throws Exception {
        UUID investigationId = UUID.randomUUID();
        mockMvc.perform(post("/api/investigations/"
                        + investigationId
                        + "/decisions/approve")
                        .header("Authorization", bearerToken("fraud.analyst"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decisionReason":"Reason"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void complianceAnalystCanStartReview() throws Exception {
        UUID investigationId = UUID.randomUUID();
        mockMvc.perform(post("/api/investigations/"
                        + investigationId
                        + "/review/start")
                        .header("Authorization", bearerToken("compliance.analyst")))
                .andExpect(status().isNotFound());
    }

    @Test
    void readOnlyCannotUpdateInvestigationStatus() throws Exception {
        UUID investigationId = UUID.randomUUID();
        mockMvc.perform(patch("/api/investigations/" + investigationId + "/status")
                        .header("Authorization", bearerToken("readonly"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INVESTIGATING"}
                                """))
                .andExpect(status().isForbidden());
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
}
