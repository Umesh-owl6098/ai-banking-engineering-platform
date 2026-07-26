package com.umeshowl.banking.investigation.kyc;

import java.util.Map;

public record KycIndicator(
        KycIndicatorType type,
        int scoreContribution,
        String explanation,
        Map<String, Object> evidenceValues
) {
    public KycIndicator {
        evidenceValues = Map.copyOf(evidenceValues);
    }
}
