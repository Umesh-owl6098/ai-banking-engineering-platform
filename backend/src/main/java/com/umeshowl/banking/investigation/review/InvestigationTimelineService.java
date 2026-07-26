package com.umeshowl.banking.investigation.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.HumanReviewDecisionRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEvent;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import com.umeshowl.banking.investigation.report.InvestigationReport;
import com.umeshowl.banking.investigation.report.InvestigationReportStore;
import com.umeshowl.banking.investigation.review.dto.InvestigationTimelineEntryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvestigationTimelineService {

    private static final List<String> AGENT_ORDER = List.of(
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE"
    );

    private static final Map<String, String> AGENT_LABELS = Map.of(
            "FRAUD", "Fraud Completed",
            "KYC", "KYC Completed",
            "AML", "AML Completed",
            "COMPLIANCE", "Compliance Completed"
    );

    private final InvestigationCaseEventRepository eventRepository;
    private final AgentFindingRepository agentFindingRepository;
    private final HumanReviewDecisionRepository reviewDecisionRepository;
    private final InvestigationReportStore reportStore;
    private final ObjectMapper objectMapper;

    public InvestigationTimelineService(
            InvestigationCaseEventRepository eventRepository,
            AgentFindingRepository agentFindingRepository,
            HumanReviewDecisionRepository reviewDecisionRepository,
            InvestigationReportStore reportStore,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.agentFindingRepository = agentFindingRepository;
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.reportStore = reportStore;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<InvestigationTimelineEntryResponse> buildTimeline(
            InvestigationCase investigationCase
    ) {
        UUID investigationId = investigationCase.getId();
        List<InvestigationTimelineEntryResponse> entries = new ArrayList<>();

        appendIfAbsent(
                entries,
                "Investigation Created",
                InvestigationAuditEventTypes.CASE_CREATED,
                investigationCase.getCreatedAt(),
                investigationCase.getAnalystId(),
                Map.of("status", investigationCase.getStatus())
        );

        appendStoredEvents(entries, investigationId);
        appendSupervisorIfMissing(entries, investigationId);
        appendAgentCompletions(entries, investigationId);
        appendReportGenerated(entries, investigationId);
        appendReviewStarted(entries, investigationId);
        appendDecisions(entries, investigationId);

        entries.sort(Comparator
                .comparing(InvestigationTimelineEntryResponse::occurredAt)
                .thenComparing(InvestigationTimelineEntryResponse::sequence));

        resequence(entries);
        return List.copyOf(entries);
    }

    private void appendStoredEvents(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        List<InvestigationCaseEvent> events = eventRepository
                .findByInvestigationCase_IdOrderByCreatedAtAsc(investigationId);

        for (InvestigationCaseEvent event : events) {
            String label = labelForEvent(event);
            if (label == null) {
                continue;
            }

            appendIfAbsent(
                    entries,
                    label,
                    event.getEventType(),
                    event.getCreatedAt(),
                    event.getActor(),
                    parsePayload(event.getPayload())
            );
        }
    }

    private void appendSupervisorIfMissing(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        if (containsLabel(entries, "Supervisor Planned")) {
            return;
        }

        agentFindingRepository
                .findByInvestigationCase_IdAndAgentType(
                        investigationId,
                        "SUPERVISOR"
                )
                .stream()
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .min(Comparator.comparing(AgentFinding::getCreatedAt))
                .ifPresent(finding -> appendIfAbsent(
                        entries,
                        "Supervisor Planned",
                        InvestigationAuditEventTypes.SUPERVISOR_ROUTING,
                        finding.getCompletedAt() != null
                                ? finding.getCompletedAt()
                                : finding.getCreatedAt(),
                        "SUPERVISOR",
                        Map.of("agentType", "SUPERVISOR")
                ));
    }

    private void appendAgentCompletions(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        for (String agentType : AGENT_ORDER) {
            String label = AGENT_LABELS.get(agentType);
            if (containsLabel(entries, label)) {
                continue;
            }

            agentFindingRepository
                    .findByInvestigationCase_IdAndAgentType(
                            investigationId,
                            agentType
                    )
                    .stream()
                    .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                    .max(Comparator.comparing(AgentFinding::getCreatedAt))
                    .ifPresent(finding -> appendIfAbsent(
                            entries,
                            label,
                            agentType.equals("COMPLIANCE")
                                    ? InvestigationAuditEventTypes
                                            .COMPLIANCE_REVIEW_COMPLETE
                                    : InvestigationAuditEventTypes
                                            .AGENT_FINDING_PRODUCED,
                            finding.getCompletedAt() != null
                                    ? finding.getCompletedAt()
                                    : finding.getCreatedAt(),
                            agentType,
                            Map.of(
                                    "agentType", agentType,
                                    "findingId", finding.getId(),
                                    "riskLevel", finding.getRiskLevel()
                            )
                    ));
        }
    }

    private void appendReportGenerated(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        if (containsLabel(entries, "AI Report Generated")) {
            return;
        }

        reportStore.findLatest(investigationId).ifPresent(report ->
                appendIfAbsent(
                        entries,
                        "AI Report Generated",
                        InvestigationAuditEventTypes.INVESTIGATION_COMPLETE,
                        report.metadata().generatedAt(),
                        report.metadata().modelName(),
                        Map.of(
                                "generationMode",
                                report.metadata().generationMode(),
                                "promptVersion",
                                report.metadata().promptVersion()
                        )
                )
        );
    }

    private void appendReviewStarted(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        if (containsLabel(entries, "Human Review Started")) {
            return;
        }

        eventRepository
                .findByInvestigationCase_IdOrderByCreatedAtAsc(investigationId)
                .stream()
                .filter(event ->
                        InvestigationAuditEventTypes.ANALYST_NOTE
                                .equals(event.getEventType())
                )
                .map(event -> parsePayload(event.getPayload()))
                .filter(payload ->
                        "HUMAN_REVIEW_STARTED".equals(
                                payload.get("stage")
                        )
                )
                .findFirst()
                .ifPresent(payload -> appendIfAbsent(
                        entries,
                        "Human Review Started",
                        InvestigationAuditEventTypes.ANALYST_NOTE,
                        OffsetDateTime.parse(
                                String.valueOf(payload.get("startedAt"))
                        ),
                        payload.get("reviewerId") == null
                                ? null
                                : String.valueOf(payload.get("reviewerId")),
                        payload
                ));
    }

    private void appendDecisions(
            List<InvestigationTimelineEntryResponse> entries,
            UUID investigationId
    ) {
        List<HumanReviewDecision> decisions = reviewDecisionRepository
                .findByInvestigationCase_IdOrderByDecisionAtAsc(investigationId);

        for (HumanReviewDecision decision : decisions) {
            entries.add(new InvestigationTimelineEntryResponse(
                    entries.size() + 1,
                    "Decision Recorded",
                    InvestigationAuditEventTypes.HUMAN_DECISION,
                    decision.getDecisionAt(),
                    decision.getReviewerId(),
                    Map.of(
                            "decision", decision.getDecision(),
                            "reason", decision.getReason() == null
                                    ? ""
                                    : decision.getReason()
                    )
            ));
        }
    }

    private String labelForEvent(InvestigationCaseEvent event) {
        return switch (event.getEventType()) {
            case InvestigationAuditEventTypes.CASE_CREATED ->
                    "Investigation Created";
            case InvestigationAuditEventTypes.SUPERVISOR_ROUTING ->
                    "Supervisor Planned";
            case InvestigationAuditEventTypes.AGENT_FINDING_PRODUCED ->
                    agentLabel(parsePayload(event.getPayload()));
            case InvestigationAuditEventTypes.COMPLIANCE_REVIEW_COMPLETE ->
                    "Compliance Completed";
            case InvestigationAuditEventTypes.INVESTIGATION_COMPLETE ->
                    "AI Report Generated";
            case InvestigationAuditEventTypes.ANALYST_NOTE -> {
                Map<String, Object> payload = parsePayload(event.getPayload());
                if ("HUMAN_REVIEW_STARTED".equals(payload.get("stage"))) {
                    yield "Human Review Started";
                }
                yield null;
            }
            case InvestigationAuditEventTypes.HUMAN_DECISION -> null;
            case InvestigationAuditEventTypes.CLARIFICATION_REQUESTED ->
                    "More Investigation Requested";
            case InvestigationAuditEventTypes.INVESTIGATION_ASSIGNED ->
                    "Investigation Assigned";
            case InvestigationAuditEventTypes.INVESTIGATION_CLAIMED ->
                    "Investigation Claimed";
            case InvestigationAuditEventTypes.INVESTIGATION_REASSIGNED ->
                    "Investigation Reassigned";
            case InvestigationAuditEventTypes.INVESTIGATION_UNASSIGNED ->
                    "Investigation Unassigned";
            case InvestigationAuditEventTypes.ANALYST_REVIEW_STARTED ->
                    "Analyst Review Started";
            case InvestigationAuditEventTypes.CASE_CLOSED ->
                    "Investigation Closed";
            default -> null;
        };
    }

    private String agentLabel(Map<String, Object> payload) {
        Object agentType = payload.get("agentType");
        if (agentType == null) {
            return null;
        }
        return AGENT_LABELS.get(String.valueOf(agentType));
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
            return Map.of("rawPayload", payloadJson);
        }
    }

    private void appendIfAbsent(
            List<InvestigationTimelineEntryResponse> entries,
            String label,
            String eventType,
            OffsetDateTime occurredAt,
            String actor,
            Map<String, Object> payload
    ) {
        if (containsLabel(entries, label)) {
            return;
        }

        entries.add(new InvestigationTimelineEntryResponse(
                entries.size() + 1,
                label,
                eventType,
                occurredAt,
                actor,
                payload == null ? Map.of() : Map.copyOf(payload)
        ));
    }

    private boolean containsLabel(
            List<InvestigationTimelineEntryResponse> entries,
            String label
    ) {
        return entries.stream()
                .anyMatch(entry -> label.equals(entry.label()));
    }

    private void resequence(
            List<InvestigationTimelineEntryResponse> entries
    ) {
        for (int index = 0; index < entries.size(); index++) {
            InvestigationTimelineEntryResponse entry = entries.get(index);
            entries.set(
                    index,
                    new InvestigationTimelineEntryResponse(
                            index + 1,
                            entry.label(),
                            entry.eventType(),
                            entry.occurredAt(),
                            entry.actor(),
                            entry.payload()
                    )
            );
        }
    }
}
