package com.umeshowl.banking.auth;

import com.umeshowl.banking.auth.dto.LoginRequest;
import com.umeshowl.banking.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("security-test")
class AuthenticationServiceTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("fraud.analyst");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(Role.FRAUD_ANALYST);
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        LoginResponse response = authenticationService.login(
                new LoginRequest("fraud.analyst", "Password123!")
        );

        assertEquals("fraud.analyst", response.username());
        assertEquals(Role.FRAUD_ANALYST, response.role());
        assertEquals("Bearer", response.tokenType());
    }

    @Test
    void loginFailsWithInvalidCredentials() {
        assertThrows(
                ResponseStatusException.class,
                () -> authenticationService.login(
                        new LoginRequest("fraud.analyst", "wrong-password")
                )
        );
    }
}
