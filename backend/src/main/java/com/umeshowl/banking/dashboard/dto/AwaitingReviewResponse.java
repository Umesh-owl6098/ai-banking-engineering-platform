package com.umeshowl.banking.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AwaitingReviewResponse(
        UUID investigationId,
        String reference,
        String customerName,
        String severity,
        String finalRecommendation,
        Integer confidencePercent,
        long waitingDurationMs,
        OffsetDateTime updatedAt
) {
}
