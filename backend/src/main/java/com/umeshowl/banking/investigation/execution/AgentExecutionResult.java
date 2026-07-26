package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.supervisor.AgentType;

import java.util.UUID;

public record AgentExecutionResult(
        AgentType agentType,
        AgentExecutionStatus status,
        String message,
        UUID findingId
) {
}
