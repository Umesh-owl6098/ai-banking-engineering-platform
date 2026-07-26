package com.umeshowl.banking.dashboard.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CriticalAlertGroupResponse(
        String groupKey,
        String severity,
        String customerName,
        String scenarioLabel,
        String screeningReason,
        BigDecimal totalAmount,
        String currency,
        int relatedTransactionCount,
        List<String> triggeredRules,
        OffsetDateTime detectedAt,
        UUID investigationId,
        String investigationStatus
) {
}
