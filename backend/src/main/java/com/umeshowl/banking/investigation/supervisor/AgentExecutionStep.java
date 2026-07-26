package com.umeshowl.banking.investigation.supervisor;

import java.util.Objects;

public record AgentExecutionStep(
        AgentType agentType,
        String selectionReason
) {
    public AgentExecutionStep {
        Objects.requireNonNull(agentType, "Agent type is required");
        Objects.requireNonNull(
                selectionReason,
                "Selection reason is required"
        );
    }
}
