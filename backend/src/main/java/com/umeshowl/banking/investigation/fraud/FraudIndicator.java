package com.umeshowl.banking.investigation.fraud;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FraudIndicator(
        FraudIndicatorType type,
        int scoreContribution,
        String explanation,
        Map<String, Object> evidenceValues,
        List<UUID> relatedTransactionIds
) {
    public FraudIndicator {
        Objects.requireNonNull(type, "Indicator type is required");
        Objects.requireNonNull(
                explanation,
                "Indicator explanation is required"
        );
        evidenceValues = Map.copyOf(evidenceValues);
        relatedTransactionIds = List.copyOf(relatedTransactionIds);
    }
}
