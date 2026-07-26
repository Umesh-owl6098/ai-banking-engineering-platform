package com.umeshowl.banking.investigation.supervisor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InvestigationExecutionPlan(
        UUID investigationId,
        List<AgentExecutionStep> steps,
        ExecutionPlanStatus status,
        OffsetDateTime createdAt
) {
    public InvestigationExecutionPlan {
        Objects.requireNonNull(
                investigationId,
                "Investigation ID is required"
        );
        steps = List.copyOf(steps);
        Objects.requireNonNull(status, "Plan status is required");
        Objects.requireNonNull(createdAt, "Plan creation time is required");
    }
}
