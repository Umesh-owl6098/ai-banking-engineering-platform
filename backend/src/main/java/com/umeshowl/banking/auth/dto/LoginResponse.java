package com.umeshowl.banking.auth.dto;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.Role;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UUID userId,
        String username,
        Role role
) {
    public static LoginResponse from(
            String accessToken,
            AuthenticatedUser user
    ) {
        return new LoginResponse(
                accessToken,
                "Bearer",
                user.id(),
                user.username(),
                user.role()
        );
    }
}
