package com.umeshowl.banking.investigation.review;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.observability.BankingMetrics;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.assignment.InvestigationAssignmentService;
import com.umeshowl.banking.notification.NotificationPublisher;
import com.umeshowl.banking.investigation.review.dto.HumanReviewDecisionRequest;
import com.umeshowl.banking.investigation.review.dto.HumanReviewDecisionResponse;
import com.umeshowl.banking.investigation.review.dto.HumanReviewNotesRequest;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewContextResponse;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InvestigationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationDecisionService.class
    );

    private static final Set<String> REVIEWABLE_STATUSES = Set.of(
            "AWAITING_REVIEW",
            "IN_REVIEW"
    );

    private final InvestigationCaseService investigationCaseService;
    private final HumanReviewDecisionRepository reviewDecisionRepository;
    private final AgentFindingRepository agentFindingRepository;
    private final InvestigationAuditService auditService;
    private final HumanReviewService humanReviewService;
    private final InvestigationAssignmentService assignmentService;
    private final NotificationPublisher notificationPublisher;
    private final CurrentUserService currentUserService;
    private final BankingMetrics bankingMetrics;

    public InvestigationDecisionService(
            InvestigationCaseService investigationCaseService,
            HumanReviewDecisionRepository reviewDecisionRepository,
            AgentFindingRepository agentFindingRepository,
            InvestigationAuditService auditService,
            HumanReviewService humanReviewService,
            InvestigationAssignmentService assignmentService,
            NotificationPublisher notificationPublisher,
            CurrentUserService currentUserService,
            BankingMetrics bankingMetrics
    ) {
        this.investigationCaseService = investigationCaseService;
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.agentFindingRepository = agentFindingRepository;
        this.auditService = auditService;
        this.humanReviewService = humanReviewService;
        this.assignmentService = assignmentService;
        this.notificationPublisher = notificationPublisher;
        this.currentUserService = currentUserService;
        this.bankingMetrics = bankingMetrics;
    }

    @Transactional
    public InvestigationReviewContextResponse approve(
            UUID investigationId,
            HumanReviewDecisionRequest request
    ) {
        return recordDecision(
                investigationId,
                ReviewDecisionAction.APPROVE,
                request
        );
    }

    @Transactional
    public InvestigationReviewContextResponse reject(
            UUID investigationId,
            HumanReviewDecisionRequest request
    ) {
        return recordDecision(
                investigationId,
                ReviewDecisionAction.REJECT,
                request
        );
    }

    @Transactional
    public InvestigationReviewContextResponse escalate(
            UUID investigationId,
            HumanReviewDecisionRequest request
    ) {
        return recordDecision(
                investigationId,
                ReviewDecisionAction.ESCALATE,
                request
        );
    }

    @Transactional
    public InvestigationReviewContextResponse requestMoreInvestigation(
            UUID investigationId,
            HumanReviewDecisionRequest request
    ) {
        return recordDecision(
                investigationId,
                ReviewDecisionAction.REQUEST_MORE_INVESTIGATION,
                request
        );
    }

    private InvestigationReviewContextResponse recordDecision(
            UUID investigationId,
            ReviewDecisionAction action,
            HumanReviewDecisionRequest request
    ) {
        Timer.Sample reviewTimer = bankingMetrics.startHumanReviewTimer();

        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        AuthenticatedUser reviewer = currentUserService.requireCurrentUser();
        validateReviewableStatus(investigationCase, action, reviewer);
        validateNoFinalDecision(investigationId, action);

        humanReviewService.startReview(investigationId);

        HumanReviewNotesRequest notes = new HumanReviewNotesRequest(
                request.comments(),
                request.decisionReason(),
                request.additionalNotes()
        );
        String persistedDecision =
                ReviewNotesParser.persistedDecisionFor(action);
        String serializedReason = ReviewNotesParser.serialize(action, notes);

        HumanReviewDecision decision = new HumanReviewDecision();
        decision.setInvestigationCase(investigationCase);
        decision.setReviewerId(reviewer.username());
        decision.setDecision(persistedDecision);
        decision.setReason(serializedReason);
        findLatestComplianceFinding(investigationId)
                .ifPresent(decision::setFinding);
        reviewDecisionRepository.save(decision);

        applyStatusChanges(investigationCase, action, reviewer);
        recordDecisionAudit(
                investigationCase,
                action,
                request,
                persistedDecision,
                reviewer
        );

        bankingMetrics.recordReviewDecision(persistedDecision, reviewTimer);
        if (action == ReviewDecisionAction.ESCALATE) {
            bankingMetrics.recordInvestigationEscalated();
            notificationPublisher.notifyEscalated(
                    investigationCase,
                    reviewer.username()
            );
        }
        log.info(
                "human_review_decision action={} role={}",
                action.name(),
                reviewer.role()
        );

        return humanReviewService.getReviewContext(investigationId);
    }

    private void applyStatusChanges(
            InvestigationCase investigationCase,
            ReviewDecisionAction action,
            AuthenticatedUser reviewer
    ) {
        UUID investigationId = investigationCase.getId();
        String reviewerId = reviewer.username();

        switch (action) {
            case APPROVE -> {
                investigationCaseService.updateStatus(
                        investigationId,
                        "APPROVED"
                );
                investigationCaseService.updateStatus(
                        investigationId,
                        "CLOSED"
                );
                auditService.recordEvent(
                        investigationCaseService.getCase(investigationId),
                        InvestigationAuditEventTypes.CASE_CLOSED,
                        reviewerId,
                        Map.of("closureReason", "APPROVED")
                );
            }
            case REJECT -> {
                investigationCaseService.updateStatus(
                        investigationId,
                        "REJECTED"
                );
                investigationCaseService.updateStatus(
                        investigationId,
                        "CLOSED"
                );
                auditService.recordEvent(
                        investigationCaseService.getCase(investigationId),
                        InvestigationAuditEventTypes.CASE_CLOSED,
                        reviewerId,
                        Map.of("closureReason", "REJECTED")
                );
            }
            case ESCALATE -> investigationCaseService.updateStatus(
                    investigationId,
                    "ESCALATED"
            );
            case REQUEST_MORE_INVESTIGATION -> {
                investigationCaseService.updateStatus(
                        investigationId,
                        "INVESTIGATING"
                );
                assignmentService.clearAssignmentFields(investigationId);
                auditService.recordEvent(
                        investigationCaseService.getCase(investigationId),
                        InvestigationAuditEventTypes.CLARIFICATION_REQUESTED,
                        reviewerId,
                        Map.of("action", action.name())
                );
            }
        }
    }

    private void recordDecisionAudit(
            InvestigationCase investigationCase,
            ReviewDecisionAction action,
            HumanReviewDecisionRequest request,
            String persistedDecision,
            AuthenticatedUser reviewer
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action.name());
        payload.put("decision", persistedDecision);
        payload.put("decisionReason", request.decisionReason());
        payload.put("reviewerRole", reviewer.role().name());
        if (request.comments() != null && !request.comments().isBlank()) {
            payload.put("comments", request.comments().trim());
        }
        if (request.additionalNotes() != null
                && !request.additionalNotes().isBlank()) {
            payload.put(
                    "additionalNotes",
                    request.additionalNotes().trim()
            );
        }

        auditService.recordEvent(
                investigationCase,
                InvestigationAuditEventTypes.HUMAN_DECISION,
                reviewer.username(),
                payload
        );
    }

    private void validateReviewableStatus(
            InvestigationCase investigationCase,
            ReviewDecisionAction action,
            AuthenticatedUser reviewer
    ) {
        if (!REVIEWABLE_STATUSES.contains(investigationCase.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation status "
                            + investigationCase.getStatus()
                            + " does not allow "
                            + action.name()
            );
        }

        if ("IN_REVIEW".equals(investigationCase.getStatus())
                && !assignmentService.isSupervisor(reviewer)) {
            assignmentService.validateAssignedReviewer(
                    investigationCase,
                    reviewer
            );
        }
    }

    private void validateNoFinalDecision(
            UUID investigationId,
            ReviewDecisionAction action
    ) {
        if (action != ReviewDecisionAction.APPROVE
                && action != ReviewDecisionAction.REJECT) {
            return;
        }

        if (reviewDecisionRepository.existsByInvestigationCase_IdAndDecisionIn(
                investigationId,
                List.of("APPROVED", "REJECTED")
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A final review decision already exists for this investigation"
            );
        }
    }

    private java.util.Optional<AgentFinding> findLatestComplianceFinding(
            UUID investigationId
    ) {
        return agentFindingRepository
                .findByInvestigationCase_IdAndAgentType(
                        investigationId,
                        "COMPLIANCE"
                )
                .stream()
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .max(Comparator.comparing(AgentFinding::getCreatedAt));
    }
}
