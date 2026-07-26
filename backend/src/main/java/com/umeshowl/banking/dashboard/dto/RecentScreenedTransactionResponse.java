package com.umeshowl.banking.dashboard.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecentScreenedTransactionResponse(
        UUID transactionId,
        String transactionReference,
        String customerName,
        BigDecimal amount,
        String currency,
        String route,
        String screeningStatus,
        String screeningReason,
        List<String> triggeredRules,
        OffsetDateTime screenedAt,
        UUID investigationId
) {
}
