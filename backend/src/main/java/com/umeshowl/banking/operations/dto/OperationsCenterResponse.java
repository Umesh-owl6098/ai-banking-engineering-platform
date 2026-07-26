package com.umeshowl.banking.operations.dto;

import com.umeshowl.banking.dashboard.dto.AgentActivitySummary;

import java.time.OffsetDateTime;
import java.util.List;

public record OperationsCenterResponse(
        PlatformHealthSummary platformHealth,
        InvestigationMetricsSummary investigationMetrics,
        List<AgentActivitySummary> agentPerformance,
        List<OperationsErrorEntry> recentErrors,
        long executionFailureTotal,
        long reportFallbackTotal,
        long reportFailureTotal,
        OffsetDateTime generatedAt
) {
}
