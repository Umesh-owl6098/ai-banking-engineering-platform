package com.umeshowl.banking.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void generatesAndValidatesToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-key-with-sufficient-length");
        properties.setExpirationMs(3_600_000L);
        JwtService jwtService = new JwtService(properties);

        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("supervisor");
        user.setRole(Role.SUPERVISOR);

        String token = jwtService.generateToken(user);

        assertEquals("supervisor", jwtService.extractUsername(token));
        assertEquals(Role.SUPERVISOR, jwtService.extractRole(token));
        assertEquals(true, jwtService.isTokenValid(token, user));
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-key-with-sufficient-length");
        properties.setExpirationMs(-1_000L);
        JwtService jwtService = new JwtService(properties);

        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("readonly");
        user.setRole(Role.READ_ONLY);

        String token = jwtService.generateToken(user);

        assertThrows(
                RuntimeException.class,
                () -> jwtService.isTokenValid(token, user)
        );
    }
}
