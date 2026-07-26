package com.umeshowl.banking.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecentInvestigationResponse(
        UUID investigationId,
        String reference,
        String source,
        String customerName,
        String severity,
        String status,
        OffsetDateTime createdAt
) {
}
