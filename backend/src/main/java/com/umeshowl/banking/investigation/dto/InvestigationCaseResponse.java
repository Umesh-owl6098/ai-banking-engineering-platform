package com.umeshowl.banking.investigation.dto;

import com.umeshowl.banking.investigation.InvestigationCase;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record InvestigationCaseResponse(
        UUID id,
        UUID projectId,
        UUID conversationId,
        UUID customerId,
        UUID transactionId,
        String caseType,
        String title,
        String description,
        String status,
        String priority,
        String analystId,
        boolean autoCreated,
        String screeningStatus,
        String screeningReason,
        List<String> screeningTriggeredRules,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String executionFailureStage,
        String executionFailureMessage,
        OffsetDateTime executionFailureAt,
        UUID assignedAnalystId,
        String assignedAnalystUsername,
        OffsetDateTime assignedAt,
        OffsetDateTime reviewStartedAt,
        String assignmentNotes
) {

    public static InvestigationCaseResponse from(
            InvestigationCase investigationCase
    ) {
        List<String> triggeredRules = investigationCase
                .getScreeningTriggeredRules() == null
                ? List.of()
                : List.copyOf(
                        Arrays.asList(
                                investigationCase.getScreeningTriggeredRules()
                        )
                );

        return new InvestigationCaseResponse(
                investigationCase.getId(),
                investigationCase.getProject().getId(),
                investigationCase.getConversation() == null
                        ? null
                        : investigationCase.getConversation().getId(),
                investigationCase.getCustomer() == null
                        ? null
                        : investigationCase.getCustomer().getId(),
                investigationCase.getTransaction() == null
                        ? null
                        : investigationCase.getTransaction().getId(),
                investigationCase.getCaseType(),
                investigationCase.getTitle(),
                investigationCase.getDescription(),
                investigationCase.getStatus(),
                investigationCase.getPriority(),
                investigationCase.getAnalystId(),
                investigationCase.isAutoCreated(),
                investigationCase.getScreeningStatus(),
                investigationCase.getScreeningReason(),
                triggeredRules,
                investigationCase.getCreatedAt(),
                investigationCase.getUpdatedAt(),
                investigationCase.getExecutionFailureStage(),
                investigationCase.getExecutionFailureMessage(),
                investigationCase.getExecutionFailureAt(),
                investigationCase.getAssignedAnalystId(),
                investigationCase.getAssignedAnalystUsername(),
                investigationCase.getAssignedAt(),
                investigationCase.getReviewStartedAt(),
                investigationCase.getAssignmentNotes()
        );
    }
}
