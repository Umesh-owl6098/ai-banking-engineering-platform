package com.umeshowl.banking.investigation.review.dto;

import com.umeshowl.banking.investigation.review.ReviewStatus;

import java.time.OffsetDateTime;

public record InvestigationReviewSummaryResponse(
        ReviewStatus reviewStatus,
        String reviewUser,
        String decision,
        OffsetDateTime reviewStartedAt,
        OffsetDateTime decisionAt
) {
}
