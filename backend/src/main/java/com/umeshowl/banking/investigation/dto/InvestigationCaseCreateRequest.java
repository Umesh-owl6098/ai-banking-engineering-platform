package com.umeshowl.banking.investigation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InvestigationCaseCreateRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        UUID customerId,

        UUID transactionId,

        @NotBlank(message = "Case type is required")
        @Size(max = 50, message = "Case type cannot exceed 50 characters")
        String caseType,

        @NotBlank(message = "Case title is required")
        @Size(max = 255, message = "Case title cannot exceed 255 characters")
        String title,

        @NotBlank(message = "Case description is required")
        String description,

        @Size(max = 20, message = "Priority cannot exceed 20 characters")
        String priority,

        @Size(max = 200, message = "Analyst ID cannot exceed 200 characters")
        String analystId
) {

    @AssertTrue(
            message = "At least one customerId or transactionId is required"
    )
    public boolean hasInvestigationSubject() {
        return customerId != null || transactionId != null;
    }
}
