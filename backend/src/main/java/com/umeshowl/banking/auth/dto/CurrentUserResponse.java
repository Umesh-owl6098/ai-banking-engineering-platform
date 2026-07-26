package com.umeshowl.banking.auth.dto;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.Role;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        Role role
) {
    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.id(),
                user.username(),
                user.role()
        );
    }
}
