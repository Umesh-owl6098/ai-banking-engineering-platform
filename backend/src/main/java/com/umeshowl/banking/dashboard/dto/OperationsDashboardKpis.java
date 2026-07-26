package com.umeshowl.banking.dashboard.dto;

public record OperationsDashboardKpis(
        long transactionsProcessedToday,
        long clearedTransactions,
        long suspiciousTransactions,
        long criticalTransactions,
        long activeInvestigations,
        long awaitingHumanReview,
        long failedInvestigations,
        Long averageInvestigationDurationMs
) {
}
