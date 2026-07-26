package com.umeshowl.banking.notification.dto;

import com.umeshowl.banking.notification.Notification;
import com.umeshowl.banking.notification.NotificationSeverity;
import com.umeshowl.banking.notification.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String title,
        String message,
        NotificationType type,
        NotificationSeverity severity,
        UUID relatedInvestigationId,
        UUID relatedTransactionId,
        boolean read,
        OffsetDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUser().getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getSeverity(),
                notification.getRelatedInvestigation() == null
                        ? null
                        : notification.getRelatedInvestigation().getId(),
                notification.getRelatedTransaction() == null
                        ? null
                        : notification.getRelatedTransaction().getId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
