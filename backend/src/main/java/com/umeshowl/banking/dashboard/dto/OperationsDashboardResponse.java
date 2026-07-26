package com.umeshowl.banking.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        OperationsDashboardKpis kpis,
        List<DistributionEntry> investigationsByStatus,
        List<DistributionEntry> investigationsBySeverity,
        List<DistributionEntry> screeningResults,
        List<DistributionEntry> triggeredRuleFrequency,
        List<AgentActivitySummary> agentActivity,
        List<CriticalAlertGroupResponse> criticalAlerts,
        List<ActiveInvestigationResponse> activeInvestigations,
        List<AwaitingReviewResponse> awaitingReview,
        List<RecentScreenedTransactionResponse> recentTransactions,
        List<RecentInvestigationResponse> recentInvestigations,
        OffsetDateTime generatedAt
) {
}
