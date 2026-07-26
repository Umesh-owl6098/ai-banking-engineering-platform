package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.InvestigationNotificationHub;
import com.umeshowl.banking.notification.NotificationPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InvestigationExecutionProgressPublisherImpl
        implements InvestigationExecutionProgressPublisher {

    private final InvestigationNotificationHub notificationHub;
    private final NotificationPublisher notificationPublisher;
    private final Map<UUID, AtomicInteger> sequences =
            new ConcurrentHashMap<>();

    public InvestigationExecutionProgressPublisherImpl(
            InvestigationNotificationHub notificationHub,
            NotificationPublisher notificationPublisher
    ) {
        this.notificationHub = notificationHub;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public void publish(InvestigationExecutionEvent event) {
        notificationPublisher.handleExecutionEvent(event);
        notificationHub.publishExecutionEvent(event);
    }

    @Override
    public void resetSequence(UUID investigationId) {
        sequences.put(investigationId, new AtomicInteger(0));
    }

    public int nextSequence(UUID investigationId) {
        return sequences
                .computeIfAbsent(
                        investigationId,
                        ignored -> new AtomicInteger(0)
                )
                .incrementAndGet();
    }
}
