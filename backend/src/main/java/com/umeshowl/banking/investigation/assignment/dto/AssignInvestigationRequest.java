package com.umeshowl.banking.investigation.assignment.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignInvestigationRequest(
        @NotBlank String assigneeUsername,
        String notes
) {
}
