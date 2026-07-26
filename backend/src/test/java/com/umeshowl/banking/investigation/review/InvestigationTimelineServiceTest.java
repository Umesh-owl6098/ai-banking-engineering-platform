package com.umeshowl.banking.investigation.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.report.InvestigationReportMetadata;
import com.umeshowl.banking.investigation.report.InvestigationReportSection;
import com.umeshowl.banking.investigation.report.InvestigationReportStore;
import com.umeshowl.banking.investigation.report.InvestigationReport;
import com.umeshowl.banking.investigation.review.dto.InvestigationTimelineEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestigationTimelineServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "b0000000-0000-4000-8000-000000000002"
    );

    private InvestigationCaseEventRepository eventRepository;
    private AgentFindingRepository agentFindingRepository;
    private HumanReviewDecisionRepository reviewDecisionRepository;
    private InvestigationReportStore reportStore;
    private InvestigationTimelineService timelineService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(InvestigationCaseEventRepository.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        reviewDecisionRepository = mock(HumanReviewDecisionRepository.class);
        reportStore = mock(InvestigationReportStore.class);
        timelineService = new InvestigationTimelineService(
                eventRepository,
                agentFindingRepository,
                reviewDecisionRepository,
                reportStore,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void buildsTimelineInChronologicalOrder() {
        InvestigationCase investigationCase = buildCase();
        OffsetDateTime fraudCompleted = investigationCase.getCreatedAt().plusMinutes(5);
        OffsetDateTime reportGenerated = investigationCase.getCreatedAt().plusMinutes(20);

        AgentFinding fraudFinding = completedFinding("FRAUD", fraudCompleted);
        InvestigationReport report = sampleReport(reportGenerated);

        when(eventRepository.findByInvestigationCase_IdOrderByCreatedAtAsc(
                INVESTIGATION_ID
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "SUPERVISOR"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of(fraudFinding));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "KYC"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "AML"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "COMPLIANCE"
        )).thenReturn(List.of());
        when(reportStore.findLatest(INVESTIGATION_ID))
                .thenReturn(Optional.of(report));
        when(reviewDecisionRepository.findByInvestigationCase_IdOrderByDecisionAtAsc(
                INVESTIGATION_ID
        )).thenReturn(List.of());

        List<InvestigationTimelineEntryResponse> timeline =
                timelineService.buildTimeline(investigationCase);

        assertTrue(timeline.size() >= 3);
        assertEquals("Investigation Created", timeline.getFirst().label());
        assertEquals(
                "Fraud Completed",
                timeline.stream()
                        .filter(entry -> "Fraud Completed".equals(entry.label()))
                        .findFirst()
                        .orElseThrow()
                        .label()
        );
        assertEquals(
                "AI Report Generated",
                timeline.getLast().label()
        );
        assertTrue(isSortedByTime(timeline));
    }

    private boolean isSortedByTime(
            List<InvestigationTimelineEntryResponse> timeline
    ) {
        for (int index = 1; index < timeline.size(); index++) {
            if (timeline.get(index - 1)
                    .occurredAt()
                    .isAfter(timeline.get(index).occurredAt())) {
                return false;
            }
        }
        return true;
    }

    private InvestigationCase buildCase() {
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setTitle("Timeline case");
        investigationCase.setStatus("AWAITING_REVIEW");
        investigationCase.setCreatedAt(
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        return investigationCase;
    }

    private AgentFinding completedFinding(
            String agentType,
            OffsetDateTime completedAt
    ) {
        AgentFinding finding = new AgentFinding();
        finding.setId(UUID.randomUUID());
        finding.setAgentType(agentType);
        finding.setStatus("COMPLETE");
        finding.setRiskLevel("HIGH");
        finding.setCreatedAt(completedAt);
        finding.setCompletedAt(completedAt);
        return finding;
    }

    private InvestigationReport sampleReport(OffsetDateTime generatedAt) {
        return new InvestigationReport(
                UUID.randomUUID(),
                INVESTIGATION_ID,
                new InvestigationReportMetadata(
                        "1.0.0",
                        generatedAt,
                        "deterministic",
                        10L,
                        "DETERMINISTIC"
                ),
                "Summary",
                InvestigationReportSection.of("Overview", "Overview"),
                InvestigationReportSection.of("Customer", "Customer"),
                InvestigationReportSection.of("Fraud", "Fraud"),
                InvestigationReportSection.of("KYC", "KYC"),
                InvestigationReportSection.of("AML", "AML"),
                InvestigationReportSection.of("Compliance", "Compliance"),
                List.of(),
                "REVIEW",
                "Confidence",
                "Limitations"
        );
    }
}
