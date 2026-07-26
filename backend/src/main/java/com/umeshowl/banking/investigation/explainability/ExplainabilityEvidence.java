package com.umeshowl.banking.investigation.explainability;

import java.math.BigDecimal;
import java.util.UUID;

public record ExplainabilityEvidence(
        UUID citationId,
        String documentName,
        Integer chunkIndex,
        BigDecimal similarity,
        String excerpt,
        String relevanceExplanation
) {
}
