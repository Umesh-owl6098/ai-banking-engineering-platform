package com.umeshowl.banking.investigation.aml;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AmlAnalysisResult(
        UUID investigationId,
        UUID customerId,
        UUID transactionId,
        int totalScore,
        AmlRiskLevel riskLevel,
        String summary,
        List<AmlIndicator> triggeredIndicators,
        OffsetDateTime analyzedAt
) {
    public AmlAnalysisResult {
        triggeredIndicators = List.copyOf(triggeredIndicators);
    }
}
