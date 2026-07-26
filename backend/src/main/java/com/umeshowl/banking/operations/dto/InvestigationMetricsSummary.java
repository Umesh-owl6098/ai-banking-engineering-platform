package com.umeshowl.banking.operations.dto;

public record InvestigationMetricsSummary(
        long transactionsProcessedToday,
        long investigationsCreatedToday,
        long criticalInvestigations,
        long awaitingAnalystReview,
        long closedInvestigations,
        long failedInvestigations
) {
}
