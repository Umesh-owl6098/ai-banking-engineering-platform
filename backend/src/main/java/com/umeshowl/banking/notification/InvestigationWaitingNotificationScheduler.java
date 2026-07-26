package com.umeshowl.banking.notification;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class InvestigationWaitingNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationWaitingNotificationScheduler.class
    );

    private static final List<String> WAITING_STATUSES = List.of(
            "AWAITING_REVIEW",
            "ASSIGNED"
    );

    private final InvestigationCaseRepository investigationCaseRepository;
    private final NotificationPublisher notificationPublisher;
    private final long waitingThresholdHours;

    public InvestigationWaitingNotificationScheduler(
            InvestigationCaseRepository investigationCaseRepository,
            NotificationPublisher notificationPublisher,
            @Value("${notification.waiting-threshold-hours:24}")
            long waitingThresholdHours
    ) {
        this.investigationCaseRepository = investigationCaseRepository;
        this.notificationPublisher = notificationPublisher;
        this.waitingThresholdHours = waitingThresholdHours;
    }

    @Scheduled(fixedDelayString = "${notification.waiting-check-interval-ms:300000}")
    @Transactional
    public void notifyStaleInvestigations() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusHours(waitingThresholdHours);

        List<InvestigationCase> staleCases = investigationCaseRepository
                .findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        WAITING_STATUSES,
                        cutoff
                );

        for (InvestigationCase investigationCase : staleCases) {
            try {
                notificationPublisher.notifyWaitingTooLong(investigationCase);
            } catch (RuntimeException exception) {
                log.warn(
                        "waiting_notification_failed investigationId={} message={}",
                        investigationCase.getId(),
                        exception.getMessage()
                );
            }
        }
    }
}
