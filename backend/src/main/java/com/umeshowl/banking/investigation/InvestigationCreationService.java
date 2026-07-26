package com.umeshowl.banking.investigation;

import com.umeshowl.banking.investigation.dto.InvestigationCreatedNotification;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import com.umeshowl.banking.screening.ScreeningCompletedEvent;
import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningStatus;
import com.umeshowl.banking.simulation.SimulationScenario;
import com.umeshowl.banking.simulation.TransactionSimulationEventHub;
import com.umeshowl.banking.simulation.dto.LiveTransactionEvent;
import com.umeshowl.banking.notification.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InvestigationCreationService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationCreationService.class
    );

    private final InvestigationCaseService investigationCaseService;
    private final InvestigationCaseRepository investigationCaseRepository;
    private final InvestigationAutoCreateProperties autoCreateProperties;
    private final InvestigationNotificationHub investigationNotificationHub;
    private final TransactionSimulationEventHub transactionSimulationEventHub;
    private final MockTransactionRepository mockTransactionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationPublisher notificationPublisher;

    public InvestigationCreationService(
            InvestigationCaseService investigationCaseService,
            InvestigationCaseRepository investigationCaseRepository,
            InvestigationAutoCreateProperties autoCreateProperties,
            InvestigationNotificationHub investigationNotificationHub,
            TransactionSimulationEventHub transactionSimulationEventHub,
            MockTransactionRepository mockTransactionRepository,
            ApplicationEventPublisher applicationEventPublisher,
            NotificationPublisher notificationPublisher
    ) {
        this.investigationCaseService = investigationCaseService;
        this.investigationCaseRepository = investigationCaseRepository;
        this.autoCreateProperties = autoCreateProperties;
        this.investigationNotificationHub = investigationNotificationHub;
        this.transactionSimulationEventHub = transactionSimulationEventHub;
        this.mockTransactionRepository = mockTransactionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.notificationPublisher = notificationPublisher;
    }

    @EventListener
    @Transactional
    public void handleScreeningCompleted(ScreeningCompletedEvent event) {
        TransactionScreeningResult screeningResult =
                event.screeningResult();
        MockTransaction transaction = mockTransactionRepository
                .findByIdWithCustomer(
                        screeningResult.getTransaction().getId()
                )
                .orElse(null);

        if (transaction == null) {
            log.warn(
                    "screening_completion_handler_skipped reason=transaction_not_found transactionId={}",
                    screeningResult.getTransaction().getId()
            );
            return;
        }

        SimulationScenario scenario = resolveScenario(transaction);
        Optional<InvestigationCase> createdInvestigation =
                Optional.empty();

        try {
            createdInvestigation = createIfRequired(screeningResult);
        } catch (RuntimeException exception) {
            log.error(
                    "investigation_auto_create_failed transactionId={} message={}",
                    transaction.getId(),
                    exception.getMessage(),
                    exception
            );
        }

        java.util.UUID investigationId = createdInvestigation
                .map(InvestigationCase::getId)
                .orElseGet(() -> findExistingInvestigationId(transaction));

        try {
            transactionSimulationEventHub.publish(
                    LiveTransactionEvent.from(
                            transaction,
                            scenario,
                            screeningResult,
                            investigationId
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "live_transaction_event_publish_failed transactionId={} message={}",
                    transaction.getId(),
                    exception.getMessage(),
                    exception
            );
        }

        createdInvestigation.ifPresent(investigation -> {
            try {
                investigationNotificationHub.publish(
                        InvestigationCreatedNotification.from(
                                investigation,
                                autoCreateProperties.getDefaultProjectId()
                        )
                );
                log.info(
                        "investigation_auto_created investigationId={} transactionId={} screeningStatus={}",
                        investigation.getId(),
                        transaction.getId(),
                        screeningResult.getStatus()
                );
                applicationEventPublisher.publishEvent(
                        new InvestigationAutoCreatedEvent(
                                investigation.getId()
                        )
                );
                notificationPublisher.notifyCriticalInvestigationCreated(
                        investigation
                );
            } catch (RuntimeException exception) {
                log.error(
                        "investigation_notification_publish_failed investigationId={} message={}",
                        investigation.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        });
    }

    @Transactional
    public Optional<InvestigationCase> createIfRequired(
            TransactionScreeningResult screeningResult
    ) {
        TransactionScreeningStatus screeningStatus =
                screeningResult.getStatus();

        if (screeningStatus != TransactionScreeningStatus.SUSPICIOUS
                && screeningStatus != TransactionScreeningStatus.CRITICAL) {
            return Optional.empty();
        }

        MockTransaction transaction = mockTransactionRepository
                .findByIdWithCustomer(
                        screeningResult.getTransaction().getId()
                )
                .orElse(null);
        if (transaction == null) {
            return Optional.empty();
        }

        if (investigationCaseRepository.existsByTransaction_Id(
                transaction.getId()
        )) {
            log.debug(
                    "investigation_auto_create_skipped reason=duplicate transactionId={}",
                    transaction.getId()
            );
            return Optional.empty();
        }

        String scenarioGroupId = transaction.getScenarioGroupId();
        if (scenarioGroupId != null
                && !scenarioGroupId.isBlank()
                && investigationCaseRepository.existsByScenarioGroupId(
                        scenarioGroupId
                )) {
            log.debug(
                    "investigation_auto_create_skipped reason=duplicate_scenario_group scenarioGroupId={} transactionId={}",
                    scenarioGroupId,
                    transaction.getId()
            );
            return Optional.empty();
        }

        MockCustomer customer = transaction.getCustomer();
        String title = buildTitle(screeningResult, transaction);
        String description = buildDescription(screeningResult, transaction);
        String priority = mapPriority(screeningResult.getStatus());

        InvestigationCase investigationCase = investigationCaseService.createCase(
                autoCreateProperties.getDefaultProjectId(),
                customer == null ? null : customer.getId(),
                transaction.getId(),
                "FRAUD",
                title,
                description,
                priority,
                "system"
        );

        investigationCase.setStatus("NEW");
        investigationCase.setAutoCreated(true);
        investigationCase.setScreeningStatus(
                screeningResult.getStatus().name()
        );
        investigationCase.setScreeningReason(screeningResult.getReason());
        investigationCase.setScreeningTriggeredRules(
                screeningResult.getTriggeredRules() == null
                        ? new String[0]
                        : screeningResult.getTriggeredRules()
        );
        if (transaction.getScenarioGroupId() != null
                && !transaction.getScenarioGroupId().isBlank()) {
            investigationCase.setScenarioGroupId(
                    transaction.getScenarioGroupId()
            );
        }

        return Optional.of(
                investigationCaseRepository.save(investigationCase)
        );
    }

    private java.util.UUID findExistingInvestigationId(
            MockTransaction transaction
    ) {
        if (transaction.getScenarioGroupId() != null
                && !transaction.getScenarioGroupId().isBlank()) {
            java.util.Optional<java.util.UUID> groupInvestigation =
                    investigationCaseRepository
                            .findFirstByScenarioGroupIdOrderByCreatedAtDesc(
                                    transaction.getScenarioGroupId()
                            )
                            .map(InvestigationCase::getId);
            if (groupInvestigation.isPresent()) {
                return groupInvestigation.get();
            }
        }

        return investigationCaseRepository
                .findByTransaction_IdOrderByCreatedAtDesc(
                        transaction.getId()
                )
                .stream()
                .findFirst()
                .map(InvestigationCase::getId)
                .orElse(null);
    }

    private SimulationScenario resolveScenario(
            MockTransaction transaction
    ) {
        if (transaction.getSimulationScenario() == null
                || transaction.getSimulationScenario().isBlank()) {
            return SimulationScenario.NORMAL;
        }

        try {
            return SimulationScenario.valueOf(
                    transaction.getSimulationScenario()
            );
        } catch (IllegalArgumentException exception) {
            return SimulationScenario.MIXED;
        }
    }

    private String buildTitle(
            TransactionScreeningResult screeningResult,
            MockTransaction transaction
    ) {
        return "Auto investigation: "
                + transaction.getTransactionReference()
                + " ("
                + screeningResult.getStatus().name()
                + ")";
    }

    private String buildDescription(
            TransactionScreeningResult screeningResult,
            MockTransaction transaction
    ) {
        String rules = screeningResult.getTriggeredRules() == null
                ? ""
                : Arrays.stream(screeningResult.getTriggeredRules())
                        .map(rule -> rule.replace('_', ' ')
                                .toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "));

        return """
                Automatically created from transaction screening.
                Screening status: %s
                Screening reason: %s
                Triggered rules: %s
                Transaction reference: %s
                """.formatted(
                screeningResult.getStatus().name(),
                screeningResult.getReason(),
                rules.isBlank() ? "none" : rules,
                transaction.getTransactionReference()
        ).trim();
    }

    private String mapPriority(TransactionScreeningStatus screeningStatus) {
        return screeningStatus == TransactionScreeningStatus.CRITICAL
                ? "CRITICAL"
                : "HIGH";
    }
}
