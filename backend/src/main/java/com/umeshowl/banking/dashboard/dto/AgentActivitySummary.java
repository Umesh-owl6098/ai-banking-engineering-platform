package com.umeshowl.banking.dashboard.dto;

public record AgentActivitySummary(
        String agentType,
        long runningCount,
        long completedCount,
        long failedCount,
        Long averageDurationMs
) {
}
