package com.umeshowl.banking.investigation.kyc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KycAnalysisResult(
        UUID investigationId,
        UUID customerId,
        UUID transactionId,
        int totalScore,
        KycRiskLevel riskLevel,
        String summary,
        List<KycIndicator> triggeredIndicators,
        OffsetDateTime analyzedAt
) {
    public KycAnalysisResult {
        triggeredIndicators = List.copyOf(triggeredIndicators);
    }
}
