package com.umeshowl.banking.investigation.explainability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExplainabilityResponse(
        UUID findingId,
        UUID investigationId,
        String agentType,
        int totalScore,
        String riskLevel,
        String recommendation,
        BigDecimal confidence,
        String summary,
        List<ExplainabilityRule> triggeredRules,
        Map<String, Object> relatedCustomerFields,
        Map<String, Object> relatedTransactionFields,
        List<ExplainabilityEvidence> supportingEvidence
) {
    public ExplainabilityResponse {
        triggeredRules = List.copyOf(triggeredRules);
        relatedCustomerFields = Map.copyOf(
                relatedCustomerFields == null ? Map.of() : relatedCustomerFields
        );
        relatedTransactionFields = Map.copyOf(
                relatedTransactionFields == null
                        ? Map.of()
                        : relatedTransactionFields
        );
        supportingEvidence = List.copyOf(
                supportingEvidence == null ? List.of() : supportingEvidence
        );
    }
}
