package com.umeshowl.banking.investigation.explainability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExplainabilityRule(
        String ruleCode,
        String displayName,
        int scoreContribution,
        String explanation,
        String description,
        Map<String, Object> evidenceValues,
        Map<String, Object> thresholds,
        Map<String, Object> relatedFields,
        BigDecimal confidenceContribution,
        List<ExplainabilityEvidence> supportingEvidence
) {
    public ExplainabilityRule {
        evidenceValues = Map.copyOf(
                evidenceValues == null ? Map.of() : evidenceValues
        );
        thresholds = Map.copyOf(thresholds == null ? Map.of() : thresholds);
        relatedFields = Map.copyOf(
                relatedFields == null ? Map.of() : relatedFields
        );
        supportingEvidence = List.copyOf(
                supportingEvidence == null ? List.of() : supportingEvidence
        );
    }
}
