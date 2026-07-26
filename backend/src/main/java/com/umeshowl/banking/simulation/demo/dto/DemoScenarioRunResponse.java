package com.umeshowl.banking.simulation.demo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DemoScenarioRunResponse(
        String scenario,
        String scenarioGroupId,
        int transactionsGenerated,
        List<DemoTransactionResult> transactions,
        UUID investigationId,
        String investigationStatus,
        String screeningSummary
) {
    public record DemoTransactionResult(
            UUID transactionId,
            String transactionReference,
            String screeningStatus,
            List<String> triggeredRules,
            BigDecimal amount,
            String currency
    ) {
    }
}
