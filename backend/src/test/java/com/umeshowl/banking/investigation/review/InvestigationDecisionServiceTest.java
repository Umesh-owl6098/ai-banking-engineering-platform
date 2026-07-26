package com.umeshowl.banking.investigation.review;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.assignment.InvestigationAssignmentService;
import com.umeshowl.banking.notification.NotificationPublisher;
import com.umeshowl.banking.investigation.review.dto.HumanReviewDecisionRequest;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewContextResponse;
import com.umeshowl.banking.observability.TestBankingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationDecisionServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "b0000000-0000-4000-8000-000000000001"
    );

    private InvestigationCaseService investigationCaseService;
    private HumanReviewDecisionRepository reviewDecisionRepository;
    private AgentFindingRepository agentFindingRepository;
    private InvestigationAuditService auditService;
    private HumanReviewService humanReviewService;
    private InvestigationAssignmentService assignmentService;
    private NotificationPublisher notificationPublisher;
    private CurrentUserService currentUserService;
    private InvestigationDecisionService decisionService;
    private com.umeshowl.banking.observability.BankingMetrics bankingMetrics;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        reviewDecisionRepository = mock(HumanReviewDecisionRepository.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        auditService = mock(InvestigationAuditService.class);
        humanReviewService = mock(HumanReviewService.class);
        assignmentService = mock(InvestigationAssignmentService.class);
        notificationPublisher = mock(NotificationPublisher.class);
        currentUserService = mock(CurrentUserService.class);
        bankingMetrics = TestBankingMetrics.create();
        decisionService = new InvestigationDecisionService(
                investigationCaseService,
                reviewDecisionRepository,
                agentFindingRepository,
                auditService,
                humanReviewService,
                assignmentService,
                notificationPublisher,
                currentUserService,
                bankingMetrics
        );

        when(currentUserService.requireCurrentUser()).thenReturn(
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        "compliance.analyst",
                        Role.COMPLIANCE_ANALYST
                )
        );
        when(assignmentService.isSupervisor(any())).thenReturn(false);
    }

    @Test
    void approveClosesInvestigation() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.existsByInvestigationCase_IdAndDecisionIn(
                eq(INVESTIGATION_ID),
                any()
        )).thenReturn(false);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.approve(INVESTIGATION_ID, request());

        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "APPROVED"
        );
        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "CLOSED"
        );
        verify(reviewDecisionRepository).save(any(HumanReviewDecision.class));
    }

    @Test
    void rejectClosesInvestigation() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.existsByInvestigationCase_IdAndDecisionIn(
                eq(INVESTIGATION_ID),
                any()
        )).thenReturn(false);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.reject(INVESTIGATION_ID, request());

        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "REJECTED"
        );
        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "CLOSED"
        );
    }

    @Test
    void escalateSetsEscalatedStatus() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.escalate(INVESTIGATION_ID, request());

        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "ESCALATED"
        );
    }

    @Test
    void requestMoreInvestigationReturnsToInvestigating() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.requestMoreInvestigation(
                INVESTIGATION_ID,
                request()
        );

        verify(investigationCaseService).updateStatus(
                INVESTIGATION_ID,
                "INVESTIGATING"
        );
    }

    @Test
    void rejectsDecisionWhenInvestigationIsNotAwaitingReview() {
        InvestigationCase investigationCase = awaitingReviewCase();
        investigationCase.setStatus("INVESTIGATING");
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);

        assertThrows(
                ResponseStatusException.class,
                () -> decisionService.approve(
                        INVESTIGATION_ID,
                        request()
                )
        );
    }

    @Test
    void usesAuthenticatedReviewerIdentity() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> {
                    HumanReviewDecision decision = invocation.getArgument(0);
                    assertEquals("compliance.analyst", decision.getReviewerId());
                    return decision;
                });
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.escalate(INVESTIGATION_ID, request());
    }

    @Test
    void recordsHumanReviewDecisionMetric() {
        InvestigationCase investigationCase = awaitingReviewCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(reviewDecisionRepository.save(any(HumanReviewDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(humanReviewService.getReviewContext(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReviewContextResponse.class));

        decisionService.approve(INVESTIGATION_ID, request());

        assertEquals(
                1.0,
                bankingMetrics.reviewDecisionsTotal("APPROVED")
        );
    }

    private InvestigationCase awaitingReviewCase() {
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setStatus("AWAITING_REVIEW");
        investigationCase.setTitle("Review case");
        return investigationCase;
    }

    private HumanReviewDecisionRequest request() {
        return new HumanReviewDecisionRequest(
                "Decision reason",
                "Comments",
                "Additional notes"
        );
    }
}
