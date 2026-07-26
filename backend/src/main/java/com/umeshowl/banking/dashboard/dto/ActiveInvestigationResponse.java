package com.umeshowl.banking.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActiveInvestigationResponse(
        UUID investigationId,
        String reference,
        String customerName,
        String severity,
        String pipelineStage,
        int progressPercent,
        long elapsedDurationMs,
        String status
) {
}
