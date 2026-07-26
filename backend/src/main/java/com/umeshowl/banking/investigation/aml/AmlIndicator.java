package com.umeshowl.banking.investigation.aml;

import java.util.Map;

public record AmlIndicator(
        AmlIndicatorType type,
        int scoreContribution,
        String explanation,
        Map<String, Object> evidenceValues
) {
    public AmlIndicator {
        evidenceValues = Map.copyOf(evidenceValues);
    }
}
