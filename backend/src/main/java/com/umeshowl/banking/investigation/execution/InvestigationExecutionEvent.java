package com.umeshowl.banking.investigation.execution;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvestigationExecutionEvent(
        InvestigationExecutionEventType eventType,
        UUID investigationId,
        String caseStatus,
        String stage,
        InvestigationExecutionStageStatus stageStatus,
        String agentType,
        List<String> plannedAgents,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Long durationMs,
        String message,
        int sequence
) {
}
