package com.umeshowl.banking.investigation.fraud;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FraudAnalysisResult(
        UUID investigationId,
        UUID customerId,
        UUID transactionId,
        int totalScore,
        FraudRiskLevel riskLevel,
        String summary,
        List<FraudIndicator> triggeredIndicators,
        OffsetDateTime analyzedAt
) {
    public FraudAnalysisResult {
        Objects.requireNonNull(
                investigationId,
                "Investigation ID is required"
        );
        Objects.requireNonNull(riskLevel, "Risk level is required");
        Objects.requireNonNull(summary, "Summary is required");
        triggeredIndicators = List.copyOf(triggeredIndicators);
        Objects.requireNonNull(analyzedAt, "Analysis time is required");
    }
}
