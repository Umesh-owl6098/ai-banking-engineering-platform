package com.umeshowl.banking.investigation.review.dto;

import jakarta.validation.constraints.NotBlank;

public record HumanReviewDecisionRequest(
        @NotBlank(message = "Decision reason is required")
        String decisionReason,
        String comments,
        String additionalNotes
) {
}
