package com.umeshowl.banking.investigation.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEvent;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.assignment.InvestigationAssignmentService;
import com.umeshowl.banking.observability.TestBankingMetrics;
import com.umeshowl.banking.investigation.report.InvestigationReportStore;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewContextResponse;
import com.umeshowl.banking.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanReviewServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "b0000000-0000-4000-8000-000000000003"
    );

    private InvestigationCaseService investigationCaseService;
    private InvestigationReportStore reportStore;
    private HumanReviewDecisionRepository reviewDecisionRepository;
    private InvestigationCaseEventRepository eventRepository;
    private InvestigationTimelineService timelineService;
    private InvestigationAuditService auditService;
    private CurrentUserService currentUserService;
    private InvestigationAssignmentService assignmentService;
    private HumanReviewService humanReviewService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        reportStore = mock(InvestigationReportStore.class);
        reviewDecisionRepository = mock(HumanReviewDecisionRepository.class);
        eventRepository = mock(InvestigationCaseEventRepository.class);
        timelineService = mock(InvestigationTimelineService.class);
        auditService = mock(InvestigationAuditService.class);
        currentUserService = mock(CurrentUserService.class);
        assignmentService = mock(InvestigationAssignmentService.class);
        humanReviewService = new HumanReviewService(
                investigationCaseService,
                reportStore,
                reviewDecisionRepository,
                eventRepository,
                timelineService,
                auditService,
                new ObjectMapper().findAndRegisterModules(),
                currentUserService,
                assignmentService,
                TestBankingMetrics.create()
        );

        when(currentUserService.requireCurrentUser()).thenReturn(
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "compliance.analyst",
                        Role.COMPLIANCE_ANALYST
                )
        );
        when(assignmentService.isSupervisor(any())).thenReturn(true);
    }

    @Test
    void startReviewRecordsAuditEvent() {
        InvestigationCase investigationCase = buildCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(eventRepository.findByInvestigationCase_IdOrderByCreatedAtAsc(
                INVESTIGATION_ID
        )).thenReturn(List.of());
        when(reviewDecisionRepository.findByInvestigationCase_IdOrderByDecisionAtAsc(
                INVESTIGATION_ID
        )).thenReturn(List.of());
        when(reportStore.findLatest(INVESTIGATION_ID))
                .thenReturn(java.util.Optional.empty());
        when(timelineService.buildTimeline(investigationCase))
                .thenReturn(List.of());
        when(auditService.recordEvent(
                eq(investigationCase),
                eq(InvestigationAuditEventTypes.ANALYST_NOTE),
                eq("compliance.analyst"),
                any()
        )).thenReturn(new InvestigationCaseEvent());

        InvestigationReviewContextResponse context =
                humanReviewService.startReview(INVESTIGATION_ID);

        assertNotNull(context);
        verify(auditService).recordEvent(
                eq(investigationCase),
                eq(InvestigationAuditEventTypes.ANALYST_REVIEW_STARTED),
                eq("compliance.analyst"),
                any()
        );
    }

    @Test
    void persistsDecisionNotesAsStructuredReason() {
        HumanReviewDecision decision = new HumanReviewDecision();
        decision.setId(UUID.randomUUID());
        decision.setInvestigationCase(buildCase());
        decision.setReviewerId("compliance.analyst");
        decision.setDecision("ESCALATED");
        decision.setReason(ReviewNotesParser.serialize(
                ReviewDecisionAction.ESCALATE,
                new com.umeshowl.banking.investigation.review.dto.HumanReviewNotesRequest(
                        "Need senior review",
                        "High risk indicators",
                        "Check counterparties"
                )
        ));
        decision.setDecisionAt(OffsetDateTime.now(ZoneOffset.UTC));

        var response = com.umeshowl.banking.investigation.review.dto
                .HumanReviewDecisionResponse.from(decision);

        assertEquals("ESCALATED", response.decision());
        assertEquals(ReviewDecisionAction.ESCALATE, response.action());
        assertEquals("High risk indicators", response.decisionReason());
        assertEquals("Need senior review", response.comments());
        assertEquals("Check counterparties", response.additionalNotes());
    }

    private InvestigationCase buildCase() {
        Project project = new Project();
        project.setId(UUID.randomUUID());

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setProject(project);
        investigationCase.setStatus("AWAITING_REVIEW");
        investigationCase.setTitle("Review case");
        investigationCase.setDescription("Review description");
        investigationCase.setCaseType("FRAUD");
        investigationCase.setPriority("HIGH");
        investigationCase.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        investigationCase.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return investigationCase;
    }
}
