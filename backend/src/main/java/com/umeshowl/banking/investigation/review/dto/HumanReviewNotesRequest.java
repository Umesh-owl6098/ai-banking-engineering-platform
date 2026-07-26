package com.umeshowl.banking.investigation.review.dto;

import jakarta.validation.constraints.NotBlank;

public record HumanReviewNotesRequest(
        String comments,
        @NotBlank(message = "Decision reason is required")
        String decisionReason,
        String additionalNotes
) {
}
