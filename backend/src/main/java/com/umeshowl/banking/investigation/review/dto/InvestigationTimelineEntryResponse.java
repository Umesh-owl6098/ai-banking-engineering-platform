package com.umeshowl.banking.investigation.review.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record InvestigationTimelineEntryResponse(
        int sequence,
        String label,
        String eventType,
        OffsetDateTime occurredAt,
        String actor,
        Map<String, Object> payload
) {
    public InvestigationTimelineEntryResponse {
        payload = Map.copyOf(payload == null ? Map.of() : payload);
    }
}
