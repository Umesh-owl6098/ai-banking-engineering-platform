package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.supervisor.AgentType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvestigationExecutionSummary(
        UUID investigationId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        InvestigationExecutionStatus overallStatus,
        List<AgentExecutionResult> stepResults,
        List<AgentType> executedAgents,
        List<AgentType> skippedAgents,
        List<AgentType> failedAgents,
        List<UUID> persistedFindingIds,
        Map<String, Integer> citationCountsByAgent,
        int totalCitationCount,
        String evidenceWarning
) {
    public InvestigationExecutionSummary {
        stepResults = List.copyOf(stepResults);
        executedAgents = List.copyOf(executedAgents);
        skippedAgents = List.copyOf(skippedAgents);
        failedAgents = List.copyOf(failedAgents);
        persistedFindingIds = List.copyOf(persistedFindingIds);
        citationCountsByAgent = Map.copyOf(citationCountsByAgent);
    }
}
