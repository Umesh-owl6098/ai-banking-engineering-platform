package com.umeshowl.banking.investigation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvestigationStatusUpdateRequest(
        @NotBlank(message = "Case status is required")
        @Size(max = 50, message = "Case status cannot exceed 50 characters")
        String status
) {
}
