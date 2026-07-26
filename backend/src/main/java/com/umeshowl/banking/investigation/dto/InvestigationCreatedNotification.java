package com.umeshowl.banking.investigation.dto;

import com.umeshowl.banking.investigation.InvestigationCase;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record InvestigationCreatedNotification(
        UUID investigationId,
        UUID projectId,
        UUID customerId,
        UUID transactionId,
        String title,
        String status,
        String priority,
        boolean autoCreated,
        String screeningStatus,
        String screeningReason,
        List<String> screeningTriggeredRules,
        OffsetDateTime createdAt
) {
    public static InvestigationCreatedNotification from(
            InvestigationCase investigationCase,
            UUID projectId
    ) {
        List<String> triggeredRules = investigationCase
                .getScreeningTriggeredRules() == null
                ? List.of()
                : List.copyOf(
                        Arrays.asList(
                                investigationCase.getScreeningTriggeredRules()
                        )
                );

        return new InvestigationCreatedNotification(
                investigationCase.getId(),
                projectId,
                investigationCase.getCustomer() == null
                        ? null
                        : investigationCase.getCustomer().getId(),
                investigationCase.getTransaction() == null
                        ? null
                        : investigationCase.getTransaction().getId(),
                investigationCase.getTitle(),
                investigationCase.getStatus(),
                investigationCase.getPriority(),
                investigationCase.isAutoCreated(),
                investigationCase.getScreeningStatus(),
                investigationCase.getScreeningReason(),
                triggeredRules,
                investigationCase.getCreatedAt()
        );
    }
}
