package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.InvestigationCase;
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
import com.umeshowl.banking.investigation.supervisor.ExecutionPlanStatus;
import com.umeshowl.banking.investigation.supervisor.InvestigationExecutionPlan;
import com.umeshowl.banking.investigation.supervisor.SupervisorAgentService;
import com.umeshowl.banking.observability.TestBankingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationExecutionServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000001"
    );
    private static final UUID FINDING_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000002"
    );

    private InvestigationCaseService investigationCaseService;
    private SupervisorAgentService supervisorAgentService;
    private FraudAgentExecutionService fraudAgentExecutionService;
    private KycAgentExecutionService kycAgentExecutionService;
    private AmlAgentExecutionService amlAgentExecutionService;
    private ComplianceAgentExecutionService complianceAgentExecutionService;
    private InvestigationEvidenceService investigationEvidenceService;
    private InvestigationExecutionProgressPublisher progressPublisher;
    private InvestigationExecutionService executionService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        supervisorAgentService = mock(SupervisorAgentService.class);
        fraudAgentExecutionService = mock(
                FraudAgentExecutionService.class
        );
        kycAgentExecutionService = mock(KycAgentExecutionService.class);
        amlAgentExecutionService = mock(AmlAgentExecutionService.class);
        complianceAgentExecutionService = mock(ComplianceAgentExecutionService.class);
        investigationEvidenceService = mock(InvestigationEvidenceService.class);
        progressPublisher = mock(InvestigationExecutionProgressPublisher.class);
        executionService = new InvestigationExecutionService(
                investigationCaseService,
                supervisorAgentService,
                fraudAgentExecutionService,
                kycAgentExecutionService,
                amlAgentExecutionService,
                complianceAgentExecutionService,
                investigationEvidenceService,
                TestBankingMetrics.create(),
                progressPublisher
        );

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(investigationEvidenceService.retrieveAndPersist(INVESTIGATION_ID))
                .thenReturn(InvestigationEvidenceResult.empty(
                        INVESTIGATION_ID,
                        List.of()
                ));
    }

    @Test
    void executesFraudOnlyPlanAndReturnsPersistedFinding() {
        configurePlan(AgentType.FRAUD);
        AgentFinding finding = finding();
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding);

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                InvestigationExecutionStatus.COMPLETE,
                summary.overallStatus()
        );
        assertEquals(List.of(AgentType.FRAUD), summary.executedAgents());
        assertTrue(summary.skippedAgents().isEmpty());
        assertEquals(List.of(FINDING_ID), summary.persistedFindingIds());
        assertEquals(
                AgentExecutionStatus.COMPLETE,
                summary.stepResults().getFirst().status()
        );
        verify(fraudAgentExecutionService).execute(INVESTIGATION_ID);
    }

    @Test
    void executesFraudKycAmlAndSkipsCompliance() {
        configurePlan(
                AgentType.FRAUD,
                AgentType.KYC,
                AgentType.AML,
                AgentType.COMPLIANCE
        );
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(kycAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(kycFinding());
        when(amlAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(amlFinding());
        when(complianceAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(complianceFinding());

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                List.of(AgentType.FRAUD, AgentType.KYC, AgentType.AML, AgentType.COMPLIANCE),
                summary.executedAgents()
        );
        assertEquals(
                List.of(),
                summary.skippedAgents()
        );
        assertEquals(4, summary.stepResults().size());
        assertEquals(
                AgentExecutionStatus.COMPLETE,
                summary.stepResults().get(1).status()
        );
    }

    @Test
    void stopsAndReturnsFailedSummaryWhenFraudExecutionFails() {
        configurePlan(AgentType.FRAUD, AgentType.KYC);
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenThrow(new IllegalStateException("Unexpected failure"));

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                InvestigationExecutionStatus.FAILED,
                summary.overallStatus()
        );
        assertEquals(List.of(AgentType.FRAUD), summary.failedAgents());
        assertTrue(summary.skippedAgents().isEmpty());
        assertEquals(1, summary.stepResults().size());
        assertEquals(
                AgentExecutionStatus.FAILED,
                summary.stepResults().getFirst().status()
        );
    }

    @Test
    void stopsAndReturnsFailedSummaryWhenKycExecutionFails() {
        configurePlan(AgentType.FRAUD, AgentType.KYC);
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(kycAgentExecutionService.execute(INVESTIGATION_ID))
                .thenThrow(new IllegalStateException("Unexpected failure"));

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                InvestigationExecutionStatus.FAILED,
                summary.overallStatus()
        );
        assertEquals(List.of(AgentType.KYC), summary.failedAgents());
        assertEquals(List.of(AgentType.FRAUD), summary.executedAgents());
        assertEquals(List.of(FINDING_ID), summary.persistedFindingIds());
    }

    @Test
    void includesCitationCountsAfterFullPipelineExecution() {
        configurePlan(
                AgentType.FRAUD,
                AgentType.KYC,
                AgentType.AML,
                AgentType.COMPLIANCE
        );
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(kycAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(kycFinding());
        when(amlAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(amlFinding());
        when(complianceAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(complianceFinding());
        when(investigationEvidenceService.retrieveAndPersist(INVESTIGATION_ID))
                .thenReturn(new InvestigationEvidenceResult(
                        INVESTIGATION_ID,
                        Map.of(),
                        Map.of(
                                "FRAUD", 1,
                                "KYC", 1,
                                "AML", 1,
                                "COMPLIANCE", 1
                        ),
                        4,
                        List.of()
                ));

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(4, summary.totalCitationCount());
        assertEquals(1, summary.citationCountsByAgent().get("FRAUD"));
        assertEquals(1, summary.citationCountsByAgent().get("COMPLIANCE"));
        verify(investigationEvidenceService).retrieveAndPersist(INVESTIGATION_ID);
    }

    @Test
    void retainsCompletedFindingsWhenEvidenceRetrievalWarns() {
        configurePlan(AgentType.FRAUD);
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(investigationEvidenceService.retrieveAndPersist(INVESTIGATION_ID))
                .thenReturn(new InvestigationEvidenceResult(
                        INVESTIGATION_ID,
                        Map.of(),
                        Map.of(),
                        0,
                        List.of("Evidence retrieval failed for FRAUD: timeout")
                ));

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                InvestigationExecutionStatus.COMPLETE,
                summary.overallStatus()
        );
        assertEquals(List.of(FINDING_ID), summary.persistedFindingIds());
        assertTrue(summary.evidenceWarning().contains("FRAUD"));
        assertEquals(0, summary.totalCitationCount());
    }

    @Test
    void failsExecutionOnlyForEvidenceIntegrityProblems() {
        configurePlan(AgentType.FRAUD);
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(investigationEvidenceService.retrieveAndPersist(INVESTIGATION_ID))
                .thenThrow(new InvestigationEvidenceIntegrityException(
                        "Chunk does not belong to document"
                ));

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(
                InvestigationExecutionStatus.FAILED,
                summary.overallStatus()
        );
        assertTrue(summary.evidenceWarning().contains("integrity"));
        assertEquals(List.of(FINDING_ID), summary.persistedFindingIds());
    }

    @Test
    void summaryContainsCompletionTimesAndStepResults() {
        configurePlan(AgentType.FRAUD, AgentType.KYC);
        when(fraudAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(finding());
        when(kycAgentExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(kycFinding());

        InvestigationExecutionSummary summary =
                executionService.execute(INVESTIGATION_ID);

        assertEquals(INVESTIGATION_ID, summary.investigationId());
        assertTrue(
                !summary.completedAt().isBefore(summary.startedAt())
        );
        assertEquals(2, summary.stepResults().size());
        assertTrue(summary.failedAgents().isEmpty());
    }

    private void configurePlan(AgentType... agentTypes) {
        List<AgentExecutionStep> steps = List.of(agentTypes).stream()
                .map(agentType -> new AgentExecutionStep(
                        agentType,
                        "Planned for test"
                ))
                .toList();
        InvestigationExecutionPlan plan =
                new InvestigationExecutionPlan(
                        INVESTIGATION_ID,
                        steps,
                        ExecutionPlanStatus.PLANNED,
                        OffsetDateTime.of(
                                2026, 1, 1, 0, 0, 0, 0,
                                ZoneOffset.UTC
                        )
                );
        when(supervisorAgentService.planInvestigation(
                INVESTIGATION_ID
        )).thenReturn(plan);
    }

    private AgentFinding finding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(FINDING_ID);
        return finding;
    }

    private AgentFinding kycFinding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(UUID.fromString(
                "70000000-0000-4000-8000-000000000003"
        ));
        return finding;
    }

    private AgentFinding amlFinding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(UUID.fromString(
                "70000000-0000-4000-8000-000000000004"
        ));
        return finding;
    }

    private AgentFinding complianceFinding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(UUID.fromString("70000000-0000-4000-8000-000000000005"));
        return finding;
    }
}
