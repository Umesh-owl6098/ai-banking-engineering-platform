package com.umeshowl.banking.investigation.assignment.dto;

import com.umeshowl.banking.investigation.InvestigationCase;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record AnalystQueueItemResponse(
        UUID investigationId,
        String reference,
        String customerName,
        String severity,
        String triggerReason,
        String status,
        String assignedAnalystUsername,
        long waitingDurationMs,
        OffsetDateTime assignedAt,
        OffsetDateTime reviewStartedAt,
        OffsetDateTime updatedAt
) {

    public static AnalystQueueItemResponse from(InvestigationCase investigationCase) {
        OffsetDateTime now = OffsetDateTime.now();
        long waitingMs = Math.max(
                0,
                ChronoUnit.MILLIS.between(investigationCase.getUpdatedAt(), now)
        );

        return new AnalystQueueItemResponse(
                investigationCase.getId(),
                resolveReference(investigationCase),
                resolveCustomerName(investigationCase),
                investigationCase.getPriority(),
                investigationCase.getScreeningReason() == null
                        ? investigationCase.getDescription()
                        : investigationCase.getScreeningReason(),
                investigationCase.getStatus(),
                investigationCase.getAssignedAnalystUsername(),
                waitingMs,
                investigationCase.getAssignedAt(),
                investigationCase.getReviewStartedAt(),
                investigationCase.getUpdatedAt()
        );
    }

    private static String resolveReference(InvestigationCase investigationCase) {
        if (investigationCase.getTransaction() != null) {
            return investigationCase.getTransaction().getTransactionReference();
        }
        return investigationCase.getTitle();
    }

    private static String resolveCustomerName(InvestigationCase investigationCase) {
        if (investigationCase.getCustomer() == null) {
            return "Unknown customer";
        }
        return investigationCase.getCustomer().getFullName();
    }
}
