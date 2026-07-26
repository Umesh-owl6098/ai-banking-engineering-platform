package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.report.InvestigationReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class InvestigationAutoExecutionService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationAutoExecutionService.class
    );

    private static final int CASE_LOOKUP_MAX_ATTEMPTS = 5;
    private static final long CASE_LOOKUP_RETRY_DELAY_MS = 100L;

    private final InvestigationCaseService investigationCaseService;
    private final InvestigationExecutionService investigationExecutionService;
    private final InvestigationReportService investigationReportService;
    private final InvestigationExecutionProgressPublisherImpl progressPublisher;

    public InvestigationAutoExecutionService(
            InvestigationCaseService investigationCaseService,
            InvestigationExecutionService investigationExecutionService,
            InvestigationReportService investigationReportService,
            InvestigationExecutionProgressPublisherImpl progressPublisher
    ) {
        this.investigationCaseService = investigationCaseService;
        this.investigationExecutionService = investigationExecutionService;
        this.investigationReportService = investigationReportService;
        this.progressPublisher = progressPublisher;
    }

    public void executeAutomatically(UUID investigationId) {
        progressPublisher.resetSequence(investigationId);
        String currentStage = "AUTO_EXECUTION_START";

        log.info(
                "investigation_auto_execution_started investigationId={}",
                investigationId
        );

        try {
            InvestigationCase investigationCase =
                    getCaseWithRetry(investigationId);
            currentStage = "STATUS_TRANSITION";

            if (!investigationCaseService.beginAutoExecution(
                    investigationId
            )) {
                InvestigationCase currentCase =
                        investigationCaseService.getCase(
                                investigationId
                        );
                log.info(
                        "investigation_auto_execution_skipped investigationId={} status={}",
                        investigationId,
                        currentCase.getStatus()
                );
                return;
            }

            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
            progressPublisher.publish(
                    executionEvent(
                            investigationId,
                            InvestigationExecutionEventType
                                    .INVESTIGATION_CREATED,
                            "RUNNING",
                            "CREATED",
                            InvestigationExecutionStageStatus.COMPLETED,
                            null,
                            null,
                            createdAt,
                            createdAt,
                            0L,
                            "Investigation created"
                    )
            );

            currentStage = "AGENT_EXECUTION";
            InvestigationExecutionSummary summary =
                    investigationExecutionService.execute(
                            investigationId
                    );

            if (summary.overallStatus()
                    != InvestigationExecutionStatus.COMPLETE) {
                failExecution(
                        investigationId,
                        currentStage,
                        "Specialist agent execution did not complete successfully"
                );
                return;
            }

            currentStage = "REPORT_GENERATION";
            OffsetDateTime reportStartedAt =
                    OffsetDateTime.now(ZoneOffset.UTC);
            progressPublisher.publish(
                    executionEvent(
                            investigationId,
                            InvestigationExecutionEventType
                                    .REPORT_GENERATION_STARTED,
                            "RUNNING",
                            "REPORT",
                            InvestigationExecutionStageStatus.RUNNING,
                            null,
                            null,
                            reportStartedAt,
                            null,
                            null,
                            "Generating investigation report"
                    )
            );

            investigationReportService.generateReport(investigationId);
            OffsetDateTime reportCompletedAt =
                    OffsetDateTime.now(ZoneOffset.UTC);

            investigationCaseService.updateStatus(
                    investigationId,
                    "REPORT_GENERATED"
            );

            progressPublisher.publish(
                    executionEvent(
                            investigationId,
                            InvestigationExecutionEventType.REPORT_GENERATED,
                            "REPORT_GENERATED",
                            "REPORT",
                            InvestigationExecutionStageStatus.COMPLETED,
                            null,
                            null,
                            reportStartedAt,
                            reportCompletedAt,
                            durationMs(reportStartedAt, reportCompletedAt),
                            "Investigation report generated"
                    )
            );

            investigationCaseService.updateStatus(
                    investigationId,
                    "AWAITING_REVIEW"
            );

            progressPublisher.publish(
                    executionEvent(
                            investigationId,
                            InvestigationExecutionEventType
                                    .INVESTIGATION_READY_FOR_REVIEW,
                            "AWAITING_REVIEW",
                            "REVIEW",
                            InvestigationExecutionStageStatus.COMPLETED,
                            null,
                            null,
                            reportCompletedAt,
                            OffsetDateTime.now(ZoneOffset.UTC),
                            durationMs(
                                    reportCompletedAt,
                                    OffsetDateTime.now(ZoneOffset.UTC)
                            ),
                            "Investigation ready for human review"
                    )
            );

            log.info(
                    "investigation_auto_execution_completed investigationId={}",
                    investigationId
            );
        } catch (RuntimeException exception) {
            log.error(
                    "investigation_auto_execution_error investigationId={} stage={} message={}",
                    investigationId,
                    currentStage,
                    exception.getMessage(),
                    exception
            );
            failExecution(
                    investigationId,
                    currentStage,
                    exception.getMessage()
            );
        }
    }

    private void failExecution(
            UUID investigationId,
            String stage,
            String message
    ) {
        String safeMessage = message == null || message.isBlank()
                ? "Investigation execution failed"
                : message;

        boolean marked = investigationCaseService.markExecutionFailed(
                investigationId,
                stage,
                safeMessage
        );

        if (!marked) {
            log.error(
                    "investigation_auto_execution_failure_not_persisted investigationId={} stage={}",
                    investigationId,
                    stage
            );
            return;
        }

        OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);
        progressPublisher.publish(
                executionEvent(
                        investigationId,
                        InvestigationExecutionEventType.EXECUTION_FAILED,
                        "EXECUTION_FAILED",
                        stage,
                        InvestigationExecutionStageStatus.FAILED,
                        null,
                        null,
                        failedAt,
                        failedAt,
                        0L,
                        safeMessage
                )
        );

        log.warn(
                "investigation_auto_execution_failed investigationId={} stage={} message={}",
                investigationId,
                stage,
                safeMessage
        );
    }

    private InvestigationCase getCaseWithRetry(UUID investigationId) {
        RuntimeException lastFailure = null;

        for (int attempt = 1;
                attempt <= CASE_LOOKUP_MAX_ATTEMPTS;
                attempt++) {
            try {
                return investigationCaseService.getCase(investigationId);
            } catch (ResponseStatusException exception) {
                lastFailure = exception;
                if (exception.getStatusCode() != HttpStatus.NOT_FOUND
                        || attempt == CASE_LOOKUP_MAX_ATTEMPTS) {
                    throw exception;
                }

                log.warn(
                        "investigation_auto_execution_case_lookup_retry investigationId={} attempt={}",
                        investigationId,
                        attempt
                );
                sleep(CASE_LOOKUP_RETRY_DELAY_MS);
            }
        }

        throw lastFailure == null
                ? new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investigation case not found: " + investigationId
                )
                : lastFailure;
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Investigation auto execution interrupted",
                    exception
            );
        }
    }

    private InvestigationExecutionEvent executionEvent(
            UUID investigationId,
            InvestigationExecutionEventType eventType,
            String caseStatus,
            String stage,
            InvestigationExecutionStageStatus stageStatus,
            String agentType,
            java.util.List<String> plannedAgents,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Long durationMs,
            String message
    ) {
        return new InvestigationExecutionEvent(
                eventType,
                investigationId,
                caseStatus,
                stage,
                stageStatus,
                agentType,
                plannedAgents,
                startedAt,
                completedAt,
                durationMs,
                message,
                progressPublisher.nextSequence(investigationId)
        );
    }

    private long durationMs(
            OffsetDateTime startedAt,
            OffsetDateTime completedAt
    ) {
        return ChronoUnit.MILLIS.between(startedAt, completedAt);
    }
}
