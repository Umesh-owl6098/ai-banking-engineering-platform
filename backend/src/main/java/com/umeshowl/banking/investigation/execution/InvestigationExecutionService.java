package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.fraud.FraudAgentExecutionService;
import com.umeshowl.banking.investigation.kyc.KycAgentExecutionService;
import com.umeshowl.banking.investigation.aml.AmlAgentExecutionService;
import com.umeshowl.banking.investigation.compliance.ComplianceAgentExecutionService;
import com.umeshowl.banking.investigation.evidence.InvestigationEvidenceIntegrityException;
import com.umeshowl.banking.investigation.evidence.InvestigationEvidenceResult;
import com.umeshowl.banking.investigation.evidence.InvestigationEvidenceService;
import com.umeshowl.banking.investigation.supervisor.AgentExecutionStep;
import com.umeshowl.banking.investigation.supervisor.AgentType;
import com.umeshowl.banking.investigation.supervisor.InvestigationExecutionPlan;
import com.umeshowl.banking.investigation.supervisor.SupervisorAgentService;
import com.umeshowl.banking.observability.BankingMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestigationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationExecutionService.class
    );

    private final InvestigationCaseService investigationCaseService;
    private final SupervisorAgentService supervisorAgentService;
    private final FraudAgentExecutionService fraudAgentExecutionService;
    private final KycAgentExecutionService kycAgentExecutionService;
    private final AmlAgentExecutionService amlAgentExecutionService;
    private final ComplianceAgentExecutionService complianceAgentExecutionService;
    private final InvestigationEvidenceService investigationEvidenceService;
    private final BankingMetrics bankingMetrics;
    private final InvestigationExecutionProgressPublisher progressPublisher;

    public InvestigationExecutionService(
            InvestigationCaseService investigationCaseService,
            SupervisorAgentService supervisorAgentService,
            FraudAgentExecutionService fraudAgentExecutionService,
            KycAgentExecutionService kycAgentExecutionService,
            AmlAgentExecutionService amlAgentExecutionService,
            ComplianceAgentExecutionService complianceAgentExecutionService,
            InvestigationEvidenceService investigationEvidenceService,
            BankingMetrics bankingMetrics,
            InvestigationExecutionProgressPublisher progressPublisher
    ) {
        this.investigationCaseService = investigationCaseService;
        this.supervisorAgentService = supervisorAgentService;
        this.fraudAgentExecutionService = fraudAgentExecutionService;
        this.kycAgentExecutionService = kycAgentExecutionService;
        this.amlAgentExecutionService = amlAgentExecutionService;
        this.complianceAgentExecutionService = complianceAgentExecutionService;
        this.investigationEvidenceService = investigationEvidenceService;
        this.bankingMetrics = bankingMetrics;
        this.progressPublisher = progressPublisher == null
                ? InvestigationExecutionProgressPublisher.noop()
                : progressPublisher;
    }

    @Transactional
    public InvestigationExecutionSummary execute(
            UUID investigationId
    ) {
        Timer.Sample executionTimer =
                bankingMetrics.startInvestigationExecutionTimer();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<AgentExecutionResult> stepResults = new ArrayList<>();
        List<AgentType> executedAgents = new ArrayList<>();
        List<AgentType> skippedAgents = new ArrayList<>();
        List<AgentType> failedAgents = new ArrayList<>();
        List<UUID> persistedFindingIds = new ArrayList<>();

        investigationCaseService.getCase(investigationId);

        OffsetDateTime supervisorStartedAt =
                OffsetDateTime.now(ZoneOffset.UTC);
        publishProgress(
                investigationId,
                InvestigationExecutionEventType.SUPERVISOR_STARTED,
                "RUNNING",
                "SUPERVISOR",
                InvestigationExecutionStageStatus.RUNNING,
                null,
                null,
                supervisorStartedAt,
                null,
                null,
                "Supervisor planning started"
        );

        InvestigationExecutionPlan plan =
                supervisorAgentService.planInvestigation(investigationId);
        OffsetDateTime supervisorCompletedAt =
                OffsetDateTime.now(ZoneOffset.UTC);
        List<String> plannedAgents = plan.steps().stream()
                .map(step -> step.agentType().name())
                .toList();

        publishProgress(
                investigationId,
                InvestigationExecutionEventType.SUPERVISOR_COMPLETED,
                "RUNNING",
                "SUPERVISOR",
                InvestigationExecutionStageStatus.COMPLETED,
                null,
                plannedAgents,
                supervisorStartedAt,
                supervisorCompletedAt,
                durationMs(supervisorStartedAt, supervisorCompletedAt),
                "Supervisor planning completed"
        );

        log.info(
                "investigation_execution_started steps={}",
                plan.steps().size()
        );

        for (AgentExecutionStep step : plan.steps()) {
            Timer.Sample agentTimer = bankingMetrics.startAgentExecutionTimer();
            OffsetDateTime agentStartedAt =
                    OffsetDateTime.now(ZoneOffset.UTC);
            publishProgress(
                    investigationId,
                    InvestigationExecutionEventType.AGENT_STARTED,
                    "RUNNING",
                    step.agentType().name(),
                    InvestigationExecutionStageStatus.RUNNING,
                    step.agentType().name(),
                    null,
                    agentStartedAt,
                    null,
                    null,
                    step.agentType().name() + " agent started"
            );

            try {
                if (step.agentType() == AgentType.FRAUD) {
                    AgentFinding finding =
                            fraudAgentExecutionService.execute(
                                    investigationId
                            );
                    UUID findingId = finding.getId();

                    executedAgents.add(AgentType.FRAUD);
                    if (findingId != null) {
                        persistedFindingIds.add(findingId);
                    }
                    stepResults.add(new AgentExecutionResult(
                            AgentType.FRAUD,
                            AgentExecutionStatus.COMPLETE,
                            "Fraud analysis completed",
                            findingId
                    ));
                    recordAgentStep(
                            AgentType.FRAUD,
                            AgentExecutionStatus.COMPLETE,
                            agentTimer
                    );
                    publishAgentCompleted(
                            investigationId,
                            AgentType.FRAUD,
                            agentStartedAt
                    );
                    continue;
                }

                if (step.agentType() == AgentType.KYC) {
                    AgentFinding finding =
                            kycAgentExecutionService.execute(
                                    investigationId
                            );
                    UUID findingId = finding.getId();

                    executedAgents.add(AgentType.KYC);
                    if (findingId != null) {
                        persistedFindingIds.add(findingId);
                    }
                    stepResults.add(new AgentExecutionResult(
                            AgentType.KYC,
                            AgentExecutionStatus.COMPLETE,
                            "KYC analysis completed",
                            findingId
                    ));
                    recordAgentStep(
                            AgentType.KYC,
                            AgentExecutionStatus.COMPLETE,
                            agentTimer
                    );
                    publishAgentCompleted(
                            investigationId,
                            AgentType.KYC,
                            agentStartedAt
                    );
                    continue;
                }

                if (step.agentType() == AgentType.AML) {
                    AgentFinding finding = amlAgentExecutionService.execute(
                            investigationId
                    );
                    UUID findingId = finding.getId();
                    executedAgents.add(AgentType.AML);
                    if (findingId != null) {
                        persistedFindingIds.add(findingId);
                    }
                    stepResults.add(new AgentExecutionResult(
                            AgentType.AML,
                            AgentExecutionStatus.COMPLETE,
                            "AML analysis completed",
                            findingId
                    ));
                    recordAgentStep(
                            AgentType.AML,
                            AgentExecutionStatus.COMPLETE,
                            agentTimer
                    );
                    publishAgentCompleted(
                            investigationId,
                            AgentType.AML,
                            agentStartedAt
                    );
                    continue;
                }

                if (step.agentType() == AgentType.COMPLIANCE) {
                    AgentFinding finding =
                            complianceAgentExecutionService.execute(
                                    investigationId
                            );
                    UUID findingId = finding.getId();
                    executedAgents.add(AgentType.COMPLIANCE);
                    if (findingId != null) {
                        persistedFindingIds.add(findingId);
                    }
                    stepResults.add(new AgentExecutionResult(
                            AgentType.COMPLIANCE,
                            AgentExecutionStatus.COMPLETE,
                            "Compliance analysis completed",
                            findingId
                    ));
                    recordAgentStep(
                            AgentType.COMPLIANCE,
                            AgentExecutionStatus.COMPLETE,
                            agentTimer
                    );
                    publishAgentCompleted(
                            investigationId,
                            AgentType.COMPLIANCE,
                            agentStartedAt
                    );
                    continue;
                }

                skippedAgents.add(step.agentType());
                stepResults.add(new AgentExecutionResult(
                        step.agentType(),
                        AgentExecutionStatus.PENDING_IMPLEMENTATION,
                        "Agent is planned but not implemented",
                        null
                ));
                recordAgentStep(
                        step.agentType(),
                        AgentExecutionStatus.PENDING_IMPLEMENTATION,
                        agentTimer
                );
            } catch (RuntimeException exception) {
                failedAgents.add(step.agentType());
                stepResults.add(new AgentExecutionResult(
                        step.agentType(),
                        AgentExecutionStatus.FAILED,
                        "Unexpected execution failure",
                        null
                ));
                bankingMetrics.recordAgentExecution(
                        step.agentType().name(),
                        AgentExecutionStatus.FAILED.name(),
                        agentTimer
                );
                publishProgress(
                        investigationId,
                        InvestigationExecutionEventType.AGENT_FAILED,
                        "RUNNING",
                        step.agentType().name(),
                        InvestigationExecutionStageStatus.FAILED,
                        step.agentType().name(),
                        null,
                        agentStartedAt,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        durationMs(
                                agentStartedAt,
                                OffsetDateTime.now(ZoneOffset.UTC)
                        ),
                        step.agentType().name() + " agent failed"
                );
                log.error(
                        "investigation_execution_step agentType={} status={}",
                        step.agentType(),
                        AgentExecutionStatus.FAILED,
                        exception
                );

                return summary(
                        executionTimer,
                        investigationId,
                        startedAt,
                        InvestigationExecutionStatus.FAILED,
                        stepResults,
                        executedAgents,
                        skippedAgents,
                        failedAgents,
                        persistedFindingIds,
                        Map.of(),
                        0,
                        null
                );
            }
        }

        Map<String, Integer> citationCountsByAgent = Map.of();
        int totalCitationCount = 0;
        String evidenceWarning = null;

        if (!executedAgents.isEmpty()) {
            try {
                InvestigationEvidenceResult evidenceResult =
                        investigationEvidenceService.retrieveAndPersist(
                                investigationId
                        );
                citationCountsByAgent =
                        evidenceResult.citationCountsByAgent();
                totalCitationCount = evidenceResult.totalCitationCount();
                if (!evidenceResult.warnings().isEmpty()) {
                    evidenceWarning = String.join("; ", evidenceResult.warnings());
                }
                log.info(
                        "investigation_evidence_completed totalCitations={} agentCount={}",
                        totalCitationCount,
                        citationCountsByAgent.size()
                );
            } catch (InvestigationEvidenceIntegrityException exception) {
                log.error(
                        "investigation_evidence_failed reason=data_integrity",
                        exception
                );
                return summary(
                        executionTimer,
                        investigationId,
                        startedAt,
                        InvestigationExecutionStatus.FAILED,
                        stepResults,
                        executedAgents,
                        skippedAgents,
                        failedAgents,
                        persistedFindingIds,
                        Map.of(),
                        0,
                        "Evidence integrity failure: "
                                + exception.getMessage()
                );
            }
        }

        return summary(
                executionTimer,
                investigationId,
                startedAt,
                InvestigationExecutionStatus.COMPLETE,
                stepResults,
                executedAgents,
                skippedAgents,
                failedAgents,
                persistedFindingIds,
                citationCountsByAgent,
                totalCitationCount,
                evidenceWarning
        );
    }

    private void recordAgentStep(
            AgentType agentType,
            AgentExecutionStatus status,
            Timer.Sample agentTimer
    ) {
        log.info(
                "investigation_execution_step agentType={} status={}",
                agentType,
                status
        );
        bankingMetrics.recordAgentExecution(
                agentType.name(),
                status.name(),
                agentTimer
        );
    }

    private void publishAgentCompleted(
            UUID investigationId,
            AgentType agentType,
            OffsetDateTime agentStartedAt
    ) {
        OffsetDateTime agentCompletedAt =
                OffsetDateTime.now(ZoneOffset.UTC);
        publishProgress(
                investigationId,
                InvestigationExecutionEventType.AGENT_COMPLETED,
                "RUNNING",
                agentType.name(),
                InvestigationExecutionStageStatus.COMPLETED,
                agentType.name(),
                null,
                agentStartedAt,
                agentCompletedAt,
                durationMs(agentStartedAt, agentCompletedAt),
                agentType.name() + " agent completed"
        );
    }

    private void publishProgress(
            UUID investigationId,
            InvestigationExecutionEventType eventType,
            String caseStatus,
            String stage,
            InvestigationExecutionStageStatus stageStatus,
            String agentType,
            List<String> plannedAgents,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Long durationMs,
            String message
    ) {
        progressPublisher.publish(
                new InvestigationExecutionEvent(
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
                )
        );
    }

    private long durationMs(
            OffsetDateTime startedAt,
            OffsetDateTime completedAt
    ) {
        return ChronoUnit.MILLIS.between(startedAt, completedAt);
    }

    private InvestigationExecutionSummary summary(
            Timer.Sample executionTimer,
            UUID investigationId,
            OffsetDateTime startedAt,
            InvestigationExecutionStatus status,
            List<AgentExecutionResult> stepResults,
            List<AgentType> executedAgents,
            List<AgentType> skippedAgents,
            List<AgentType> failedAgents,
            List<UUID> persistedFindingIds,
            Map<String, Integer> citationCountsByAgent,
            int totalCitationCount,
            String evidenceWarning
    ) {
        bankingMetrics.recordInvestigationExecutionDuration(executionTimer);
        if (status == InvestigationExecutionStatus.FAILED) {
            bankingMetrics.recordInvestigationExecutionFailure();
        }

        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);

        log.info(
                "investigation_execution_completed status={} executed={} skipped={} failed={} citations={}",
                status,
                executedAgents.size(),
                skippedAgents.size(),
                failedAgents.size(),
                totalCitationCount
        );

        return new InvestigationExecutionSummary(
                investigationId,
                startedAt,
                completedAt,
                status,
                stepResults,
                executedAgents,
                skippedAgents,
                failedAgents,
                persistedFindingIds,
                citationCountsByAgent == null
                        ? Map.of()
                        : new LinkedHashMap<>(citationCountsByAgent),
                totalCitationCount,
                evidenceWarning
        );
    }
}
