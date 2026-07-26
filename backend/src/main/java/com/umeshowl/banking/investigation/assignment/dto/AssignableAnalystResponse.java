package com.umeshowl.banking.investigation.assignment.dto;

import com.umeshowl.banking.auth.Role;

import java.util.UUID;

public record AssignableAnalystResponse(
        UUID id,
        String username,
        Role role
) {
}
