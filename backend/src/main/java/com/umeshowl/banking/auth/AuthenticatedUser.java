package com.umeshowl.banking.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        Role role
) {
}
