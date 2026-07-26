package com.umeshowl.banking.operations.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationsErrorEntry(
        String errorType,
        String message,
        String source,
        UUID investigationId,
        OffsetDateTime occurredAt
) {
}
