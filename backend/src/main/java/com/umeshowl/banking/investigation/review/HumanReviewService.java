package com.umeshowl.banking.investigation.review;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.observability.BankingMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEvent;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.assignment.InvestigationAssignmentService;
import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import com.umeshowl.banking.investigation.report.InvestigationReport;
import com.umeshowl.banking.investigation.report.InvestigationReportStore;
import com.umeshowl.banking.investigation.review.dto.HumanReviewDecisionResponse;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewContextResponse;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewSummaryResponse;
import com.umeshowl.banking.investigation.review.dto.InvestigationTimelineEntryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HumanReviewService {

    private static final Logger log = LoggerFactory.getLogger(
            HumanReviewService.class
    );

    private final InvestigationCaseService investigationCaseService;
    private final InvestigationReportStore reportStore;
    private final HumanReviewDecisionRepository reviewDecisionRepository;
    private final InvestigationCaseEventRepository eventRepository;
    private final InvestigationTimelineService timelineService;
    private final InvestigationAuditService auditService;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final InvestigationAssignmentService assignmentService;
    private final BankingMetrics bankingMetrics;

    public HumanReviewService(
            InvestigationCaseService investigationCaseService,
            InvestigationReportStore reportStore,
            HumanReviewDecisionRepository reviewDecisionRepository,
            InvestigationCaseEventRepository eventRepository,
            InvestigationTimelineService timelineService,
            InvestigationAuditService auditService,
            ObjectMapper objectMapper,
            CurrentUserService currentUserService,
            InvestigationAssignmentService assignmentService,
            BankingMetrics bankingMetrics
    ) {
        this.investigationCaseService = investigationCaseService;
        this.reportStore = reportStore;
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.eventRepository = eventRepository;
        this.timelineService = timelineService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.assignmentService = assignmentService;
        this.bankingMetrics = bankingMetrics;
    }

    @Transactional(readOnly = true)
    public InvestigationReviewContextResponse getReviewContext(
            UUID investigationId
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        InvestigationReport report = reportStore
                .findLatest(investigationId)
                .orElse(null);
        List<HumanReviewDecisionResponse> decisions =
                reviewDecisionRepository
                        .findByInvestigationCase_IdOrderByDecisionAtAsc(
                                investigationId
                        )
                        .stream()
                        .map(HumanReviewDecisionResponse::from)
                        .toList();
        List<InvestigationTimelineEntryResponse> timeline =
                timelineService.buildTimeline(investigationCase);

        return new InvestigationReviewContextResponse(
                InvestigationCaseResponse.from(investigationCase),
                report,
                buildReviewSummary(investigationId, decisions),
                decisions,
                timeline
        );
    }

    @Transactional
    public InvestigationReviewContextResponse startReview(
            UUID investigationId
    ) {
        AuthenticatedUser reviewer = currentUserService.requireCurrentUser();
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        validateReviewableForStart(investigationCase, reviewer);

        if (!assignmentService.isSupervisor(reviewer)) {
            assignmentService.validateAssignedReviewer(
                    investigationCase,
                    reviewer
            );
        }

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (investigationCase.getReviewStartedAt() == null) {
            investigationCase.setReviewStartedAt(startedAt);
        }

        if (!"IN_REVIEW".equals(investigationCase.getStatus())) {
            investigationCaseService.updateStatus(investigationId, "IN_REVIEW");
            investigationCase = investigationCaseService.getCase(investigationId);
        }

        if (findReviewStartedEvent(investigationId).isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("stage", "HUMAN_REVIEW_STARTED");
            payload.put("reviewerId", reviewer.username());
            payload.put("reviewerRole", reviewer.role().name());
            payload.put("startedAt", startedAt.toString());
            auditService.recordEvent(
                    investigationCase,
                    InvestigationAuditEventTypes.ANALYST_REVIEW_STARTED,
                    reviewer.username(),
                    payload
            );
            auditService.recordEvent(
                    investigationCase,
                    InvestigationAuditEventTypes.ANALYST_NOTE,
                    reviewer.username(),
                    payload
            );
            bankingMetrics.recordReviewStarted();
            log.info(
                    "human_review_started role={}",
                    reviewer.role()
            );
        }

        return getReviewContext(investigationId);
    }

    InvestigationReviewSummaryResponse buildReviewSummary(
            UUID investigationId,
            List<HumanReviewDecisionResponse> decisions
    ) {
        HumanReviewDecisionResponse latestDecision = decisions.isEmpty()
                ? null
                : decisions.getLast();

        ReviewStatus reviewStatus = resolveReviewStatus(
                investigationId,
                latestDecision
        );

        return new InvestigationReviewSummaryResponse(
                reviewStatus,
                resolveReviewUser(investigationId, latestDecision),
                latestDecision == null ? null : latestDecision.decision(),
                findReviewStartedAt(investigationId).orElse(null),
                latestDecision == null ? null : latestDecision.decisionAt()
        );
    }

    private ReviewStatus resolveReviewStatus(
            UUID investigationId,
            HumanReviewDecisionResponse latestDecision
    ) {
        if (latestDecision != null
                && isTerminalDecision(latestDecision.decision())) {
            return ReviewStatus.COMPLETED;
        }

        if (findReviewStartedEvent(investigationId).isPresent()) {
            return ReviewStatus.IN_PROGRESS;
        }

        return ReviewStatus.NOT_STARTED;
    }

    private boolean isTerminalDecision(String decision) {
        return "APPROVED".equals(decision)
                || "REJECTED".equals(decision);
    }

    private String resolveReviewUser(
            UUID investigationId,
            HumanReviewDecisionResponse latestDecision
    ) {
        if (latestDecision != null) {
            return latestDecision.reviewerId();
        }

        return findReviewStartedEvent(investigationId)
                .map(event -> parsePayload(event.getPayload()).get("reviewerId"))
                .map(String::valueOf)
                .orElse(null);
    }

    private java.util.Optional<OffsetDateTime> findReviewStartedAt(
            UUID investigationId
    ) {
        return findReviewStartedEvent(investigationId)
                .map(InvestigationCaseEvent::getCreatedAt);
    }

    private java.util.Optional<InvestigationCaseEvent> findReviewStartedEvent(
            UUID investigationId
    ) {
        return eventRepository
                .findByInvestigationCase_IdOrderByCreatedAtAsc(investigationId)
                .stream()
                .filter(event ->
                        InvestigationAuditEventTypes.ANALYST_NOTE
                                .equals(event.getEventType())
                )
                .filter(event ->
                        "HUMAN_REVIEW_STARTED".equals(
                                parsePayload(event.getPayload()).get("stage")
                        )
                )
                .findFirst();
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    payloadJson,
                    new TypeReference<>() {
                    }
            );
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private void validateReviewableForStart(
            InvestigationCase investigationCase,
            AuthenticatedUser reviewer
    ) {
        String status = investigationCase.getStatus();
        if (List.of("ASSIGNED", "IN_REVIEW", "ESCALATED").contains(status)) {
            return;
        }

        if ("AWAITING_REVIEW".equals(status)
                && assignmentService.isSupervisor(reviewer)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Investigation is not ready for human review: " + status
        );
    }
}
