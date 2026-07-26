package com.umeshowl.banking.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.dashboard.dto.*;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.report.InvestigationReportEntity;
import com.umeshowl.banking.investigation.report.InvestigationReportRepository;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningResultRepository;
import com.umeshowl.banking.screening.TransactionScreeningStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OperationsDashboardService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            "NEW",
            "RUNNING",
            "REPORT_GENERATED"
    );
    private static final List<String> COMPLETED_STATUSES = List.of(
            "AWAITING_REVIEW",
            "APPROVED",
            "REJECTED",
            "CLOSED",
            "ESCALATED"
    );
    private static final List<String> AGENT_TYPES = List.of(
            "SUPERVISOR",
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE",
            "REPORT"
    );
    private static final List<String> PIPELINE_STAGES = List.of(
            "SUPERVISOR",
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE",
            "REPORT"
    );

    private final InvestigationCaseRepository investigationCaseRepository;
    private final TransactionScreeningResultRepository screeningResultRepository;
    private final AgentFindingRepository agentFindingRepository;
    private final InvestigationReportRepository investigationReportRepository;
    private final ObjectMapper objectMapper;
    private final UUID defaultProjectId;

    public OperationsDashboardService(
            InvestigationCaseRepository investigationCaseRepository,
            TransactionScreeningResultRepository screeningResultRepository,
            AgentFindingRepository agentFindingRepository,
            InvestigationReportRepository investigationReportRepository,
            ObjectMapper objectMapper,
            @Value("${investigation.auto-create.default-project-id}")
            UUID defaultProjectId
    ) {
        this.investigationCaseRepository = investigationCaseRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.agentFindingRepository = agentFindingRepository;
        this.investigationReportRepository = investigationReportRepository;
        this.objectMapper = objectMapper;
        this.defaultProjectId = defaultProjectId;
    }

    @Transactional(readOnly = true)
    public OperationsDashboardResponse loadOperationsDashboard(UUID projectId) {
        UUID resolvedProjectId = projectId == null
                ? defaultProjectId
                : projectId;
        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS);

        OperationsDashboardKpis kpis = buildKpis(resolvedProjectId, startOfToday);
        List<DistributionEntry> investigationsByStatus =
                toDistribution(
                        investigationCaseRepository.countByProjectGroupedByStatus(
                                resolvedProjectId
                        )
                );
        List<DistributionEntry> investigationsBySeverity =
                toDistribution(
                        investigationCaseRepository.countByProjectGroupedByPriority(
                                resolvedProjectId
                        )
                );
        List<DistributionEntry> screeningResults =
                buildScreeningDistribution(startOfToday);
        List<DistributionEntry> triggeredRuleFrequency =
                buildTriggeredRuleFrequency();

        List<InvestigationCase> projectCases =
                investigationCaseRepository
                        .findByProject_IdOrderByCreatedAtDesc(resolvedProjectId);
        Map<UUID, InvestigationCase> investigationByTransactionId =
                projectCases.stream()
                        .filter(item -> item.getTransaction() != null)
                        .collect(Collectors.toMap(
                                item -> item.getTransaction().getId(),
                                item -> item,
                                (left, right) -> left
                        ));
        Map<String, InvestigationCase> investigationByScenarioGroup =
                projectCases.stream()
                        .filter(item -> item.getScenarioGroupId() != null)
                        .collect(Collectors.toMap(
                                InvestigationCase::getScenarioGroupId,
                                item -> item,
                                (left, right) ->
                                        left.getCreatedAt().isAfter(right.getCreatedAt())
                                                ? left
                                                : right
                        ));

        return new OperationsDashboardResponse(
                kpis,
                investigationsByStatus,
                investigationsBySeverity,
                screeningResults,
                triggeredRuleFrequency,
                buildAgentActivity(resolvedProjectId, projectCases),
                buildCriticalAlerts(
                        investigationByTransactionId,
                        investigationByScenarioGroup
                ),
                buildActiveInvestigations(projectCases),
                buildAwaitingReview(projectCases),
                buildRecentTransactions(
                        investigationByTransactionId
                ),
                buildRecentInvestigations(projectCases),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private OperationsDashboardKpis buildKpis(
            UUID projectId,
            OffsetDateTime startOfToday
    ) {
        long processedToday =
                screeningResultRepository.countByScreenedAtGreaterThanEqual(
                        startOfToday
                );
        long clearedToday =
                screeningResultRepository.countByStatusAndScreenedAtGreaterThanEqual(
                        TransactionScreeningStatus.CLEARED,
                        startOfToday
                );
        long suspiciousToday =
                screeningResultRepository.countByStatusAndScreenedAtGreaterThanEqual(
                        TransactionScreeningStatus.SUSPICIOUS,
                        startOfToday
                );
        long criticalToday =
                screeningResultRepository.countByStatusAndScreenedAtGreaterThanEqual(
                        TransactionScreeningStatus.CRITICAL,
                        startOfToday
                );

        long activeInvestigations = ACTIVE_STATUSES.stream()
                .mapToLong(status ->
                        investigationCaseRepository.countByProject_IdAndStatus(
                                projectId,
                                status
                        ))
                .sum();
        long awaitingReview =
                investigationCaseRepository.countByProject_IdAndStatus(
                        projectId,
                        "AWAITING_REVIEW"
                );
        long failedInvestigations =
                investigationCaseRepository.countByProject_IdAndStatus(
                        projectId,
                        "EXECUTION_FAILED"
                );
        Double averageDuration =
                investigationCaseRepository.averageCompletedDurationMs(
                        projectId
                );

        return new OperationsDashboardKpis(
                processedToday,
                clearedToday,
                suspiciousToday,
                criticalToday,
                activeInvestigations,
                awaitingReview,
                failedInvestigations,
                averageDuration == null
                        ? null
                        : averageDuration.longValue()
        );
    }

    private List<DistributionEntry> buildScreeningDistribution(
            OffsetDateTime startOfToday
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TransactionScreeningStatus status : TransactionScreeningStatus.values()) {
            long value = screeningResultRepository
                    .countByStatusAndScreenedAtGreaterThanEqual(
                            status,
                            startOfToday
                    );
            if (value > 0) {
                counts.put(status.name(), value);
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new DistributionEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(DistributionEntry::value).reversed())
                .toList();
    }

    private List<DistributionEntry> buildTriggeredRuleFrequency() {
        Map<String, Long> counts = new HashMap<>();
        for (TransactionScreeningResult screeningResult :
                screeningResultRepository.findRecentScreened(PageRequest.of(0, 200))) {
            if (screeningResult.getTriggeredRules() == null) {
                continue;
            }
            for (String rule : screeningResult.getTriggeredRules()) {
                counts.merge(rule, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new DistributionEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(DistributionEntry::value).reversed())
                .limit(12)
                .toList();
    }

    private List<DistributionEntry> toDistribution(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DistributionEntry(
                        String.valueOf(row[0]),
                        ((Number) row[1]).longValue()
                ))
                .sorted(Comparator.comparingLong(DistributionEntry::value).reversed())
                .toList();
    }

    private List<AgentActivitySummary> buildAgentActivity(
            UUID projectId,
            List<InvestigationCase> projectCases
    ) {
        Map<String, AgentActivityAccumulator> accumulators = new LinkedHashMap<>();
        for (String agentType : AGENT_TYPES) {
            accumulators.put(agentType, new AgentActivityAccumulator());
        }

        for (Object[] row : agentFindingRepository.aggregateByAgentTypeAndStatus(
                projectId
        )) {
            String agentType = String.valueOf(row[0]);
            String status = String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();
            Double averageDuration = row[3] == null
                    ? null
                    : ((Number) row[3]).doubleValue();

            AgentActivityAccumulator accumulator =
                    accumulators.computeIfAbsent(
                            agentType,
                            ignored -> new AgentActivityAccumulator()
                    );
            if ("PENDING".equals(status)) {
                accumulator.runningCount += count;
            } else if ("COMPLETE".equals(status)) {
                accumulator.completedCount += count;
                if (averageDuration != null) {
                    accumulator.addDuration(averageDuration, count);
                }
            }
        }

        long reportCompleted = projectCases.stream()
                .filter(item -> List.of(
                        "AWAITING_REVIEW",
                        "APPROVED",
                        "REJECTED",
                        "CLOSED",
                        "ESCALATED"
                ).contains(item.getStatus()))
                .count();
        long reportRunning = projectCases.stream()
                .filter(item -> "REPORT_GENERATED".equals(item.getStatus()))
                .count();
        AgentActivityAccumulator reportAccumulator = accumulators.get("REPORT");
        reportAccumulator.completedCount = reportCompleted;
        reportAccumulator.runningCount = reportRunning;

        long supervisorRunning = projectCases.stream()
                .filter(item -> "NEW".equals(item.getStatus()))
                .count();
        long supervisorCompleted = projectCases.stream()
                .filter(item -> !"NEW".equals(item.getStatus()))
                .count();
        AgentActivityAccumulator supervisorAccumulator =
                accumulators.get("SUPERVISOR");
        supervisorAccumulator.runningCount = supervisorRunning;
        supervisorAccumulator.completedCount = supervisorCompleted;

        for (InvestigationCase investigationCase : projectCases) {
            if (!"EXECUTION_FAILED".equals(investigationCase.getStatus())
                    || investigationCase.getExecutionFailureStage() == null) {
                continue;
            }

            String failedStage = investigationCase.getExecutionFailureStage()
                    .toUpperCase(Locale.ROOT);
            AgentActivityAccumulator accumulator = accumulators.get(failedStage);
            if (accumulator != null) {
                accumulator.failedCount += 1;
            }
        }

        return AGENT_TYPES.stream()
                .map(agentType -> {
                    AgentActivityAccumulator accumulator =
                            accumulators.get(agentType);
                    return new AgentActivitySummary(
                            agentType,
                            accumulator.runningCount,
                            accumulator.completedCount,
                            accumulator.failedCount,
                            accumulator.averageDurationMs()
                    );
                })
                .toList();
    }

    private List<CriticalAlertGroupResponse> buildCriticalAlerts(
            Map<UUID, InvestigationCase> investigationByTransactionId,
            Map<String, InvestigationCase> investigationByScenarioGroup
    ) {
        List<TransactionScreeningResult> alertResults =
                screeningResultRepository.findRecentByStatuses(
                        List.of(
                                TransactionScreeningStatus.SUSPICIOUS,
                                TransactionScreeningStatus.CRITICAL
                        ),
                        PageRequest.of(0, 60)
                );

        Map<String, List<TransactionScreeningResult>> grouped =
                new LinkedHashMap<>();
        for (TransactionScreeningResult screeningResult : alertResults) {
            MockTransaction transaction = screeningResult.getTransaction();
            String groupKey = transaction.getScenarioGroupId() != null
                    ? transaction.getScenarioGroupId()
                    : transaction.getCustomer().getId() + ":"
                            + screeningResult.getStatus().name();
            grouped.computeIfAbsent(groupKey, ignored -> new ArrayList<>())
                    .add(screeningResult);
        }

        return grouped.values().stream()
                .map(group -> toCriticalAlertGroup(
                        group,
                        investigationByTransactionId,
                        investigationByScenarioGroup
                ))
                .sorted(Comparator.comparing(CriticalAlertGroupResponse::detectedAt)
                        .reversed())
                .limit(12)
                .toList();
    }

    private CriticalAlertGroupResponse toCriticalAlertGroup(
            List<TransactionScreeningResult> group,
            Map<UUID, InvestigationCase> investigationByTransactionId,
            Map<String, InvestigationCase> investigationByScenarioGroup
    ) {
        TransactionScreeningResult latest = group.stream()
                .max(Comparator.comparing(TransactionScreeningResult::getScreenedAt))
                .orElseThrow();
        MockTransaction latestTransaction = latest.getTransaction();
        MockCustomer customer = latestTransaction.getCustomer();

        BigDecimal totalAmount = group.stream()
                .map(item -> item.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> mergedRules = new LinkedHashSet<>();
        for (TransactionScreeningResult screeningResult : group) {
            if (screeningResult.getTriggeredRules() != null) {
                mergedRules.addAll(
                        Arrays.asList(screeningResult.getTriggeredRules())
                );
            }
        }

        InvestigationCase investigationCase = null;
        if (latestTransaction.getScenarioGroupId() != null) {
            investigationCase = investigationByScenarioGroup.get(
                    latestTransaction.getScenarioGroupId()
            );
        }
        if (investigationCase == null) {
            investigationCase = investigationByTransactionId.get(
                    latestTransaction.getId()
            );
        }

        String scenarioLabel = latestTransaction.getSimulationScenario() != null
                ? latestTransaction.getSimulationScenario()
                : latestTransaction.getScenarioGroupId() != null
                        ? latestTransaction.getScenarioGroupId()
                        : "Live screening";

        return new CriticalAlertGroupResponse(
                latestTransaction.getScenarioGroupId() != null
                        ? latestTransaction.getScenarioGroupId()
                        : latestTransaction.getId().toString(),
                latest.getStatus().name(),
                customer.getFullName(),
                scenarioLabel,
                latest.getReason(),
                totalAmount,
                latestTransaction.getCurrency(),
                group.size(),
                List.copyOf(mergedRules),
                latest.getScreenedAt(),
                investigationCase == null ? null : investigationCase.getId(),
                investigationCase == null ? null : investigationCase.getStatus()
        );
    }

    private List<ActiveInvestigationResponse> buildActiveInvestigations(
            List<InvestigationCase> projectCases
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return projectCases.stream()
                .filter(item -> ACTIVE_STATUSES.contains(item.getStatus()))
                .sorted(Comparator.comparing(InvestigationCase::getUpdatedAt)
                        .reversed())
                .limit(12)
                .map(investigationCase -> {
                    List<AgentFinding> findings =
                            agentFindingRepository
                                    .findByInvestigationCase_IdOrderByCreatedAtAsc(
                                            investigationCase.getId()
                                    );
                    int completedStages = countCompletedStages(
                            investigationCase,
                            findings
                    );
                    int progressPercent = Math.min(
                            100,
                            Math.round(
                                    (completedStages * 100f) / PIPELINE_STAGES.size()
                            )
                    );

                    return new ActiveInvestigationResponse(
                            investigationCase.getId(),
                            resolveReference(investigationCase),
                            resolveCustomerName(investigationCase),
                            investigationCase.getPriority(),
                            resolvePipelineStage(investigationCase, findings),
                            progressPercent,
                            Math.max(
                                    0,
                                    ChronoUnit.MILLIS.between(
                                            investigationCase.getCreatedAt(),
                                            now
                                    )
                            ),
                            investigationCase.getStatus()
                    );
                })
                .toList();
    }

    private List<AwaitingReviewResponse> buildAwaitingReview(
            List<InvestigationCase> projectCases
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return projectCases.stream()
                .filter(item -> "AWAITING_REVIEW".equals(item.getStatus()))
                .sorted(Comparator.comparing(InvestigationCase::getUpdatedAt)
                        .reversed())
                .limit(12)
                .map(investigationCase -> {
                    List<AgentFinding> findings =
                            agentFindingRepository
                                    .findByInvestigationCase_IdOrderByCreatedAtAsc(
                                            investigationCase.getId()
                                    );
                    Optional<InvestigationReportEntity> report =
                            investigationReportRepository
                                    .findFirstByInvestigationCase_IdOrderByGeneratedAtDesc(
                                            investigationCase.getId()
                                    );

                    return new AwaitingReviewResponse(
                            investigationCase.getId(),
                            resolveReference(investigationCase),
                            resolveCustomerName(investigationCase),
                            investigationCase.getPriority(),
                            resolveRecommendation(report, findings),
                            resolveConfidencePercent(findings),
                            Math.max(
                                    0,
                                    ChronoUnit.MILLIS.between(
                                            investigationCase.getUpdatedAt(),
                                            now
                                    )
                            ),
                            investigationCase.getUpdatedAt()
                    );
                })
                .toList();
    }

    private List<RecentScreenedTransactionResponse> buildRecentTransactions(
            Map<UUID, InvestigationCase> investigationByTransactionId
    ) {
        return screeningResultRepository.findRecentScreened(PageRequest.of(0, 15))
                .stream()
                .map(screeningResult -> {
                    MockTransaction transaction =
                            screeningResult.getTransaction();
                    MockCustomer customer = transaction.getCustomer();
                    InvestigationCase investigationCase =
                            investigationByTransactionId.get(transaction.getId());

                    return new RecentScreenedTransactionResponse(
                            transaction.getId(),
                            transaction.getTransactionReference(),
                            customer.getFullName(),
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            transaction.getOriginCountry()
                                    + " → "
                                    + transaction.getDestinationCountry(),
                            screeningResult.getStatus().name(),
                            screeningResult.getReason(),
                            screeningResult.getTriggeredRules() == null
                                    ? List.of()
                                    : List.copyOf(
                                            Arrays.asList(
                                                    screeningResult.getTriggeredRules()
                                            )
                                    ),
                            screeningResult.getScreenedAt(),
                            investigationCase == null
                                    ? null
                                    : investigationCase.getId()
                    );
                })
                .toList();
    }

    private List<RecentInvestigationResponse> buildRecentInvestigations(
            List<InvestigationCase> projectCases
    ) {
        return projectCases.stream()
                .limit(12)
                .map(investigationCase -> new RecentInvestigationResponse(
                        investigationCase.getId(),
                        resolveReference(investigationCase),
                        investigationCase.isAutoCreated()
                                ? "Auto-screening"
                                : "Manual",
                        resolveCustomerName(investigationCase),
                        investigationCase.getPriority(),
                        investigationCase.getStatus(),
                        investigationCase.getCreatedAt()
                ))
                .toList();
    }

    private int countCompletedStages(
            InvestigationCase investigationCase,
            List<AgentFinding> findings
    ) {
        int completed = 0;

        if (!"NEW".equals(investigationCase.getStatus())) {
            completed += 1;
        }

        for (String agentType : List.of("FRAUD", "KYC", "AML", "COMPLIANCE")) {
            boolean complete = findings.stream()
                    .anyMatch(finding ->
                            agentType.equals(finding.getAgentType())
                                    && "COMPLETE".equals(finding.getStatus())
                    );
            if (complete) {
                completed += 1;
            }
        }

        if (COMPLETED_STATUSES.contains(investigationCase.getStatus())
                || "REPORT_GENERATED".equals(investigationCase.getStatus())) {
            completed += 1;
        }

        return completed;
    }

    private String resolvePipelineStage(
            InvestigationCase investigationCase,
            List<AgentFinding> findings
    ) {
        if ("NEW".equals(investigationCase.getStatus())) {
            return "SUPERVISOR";
        }
        if ("REPORT_GENERATED".equals(investigationCase.getStatus())) {
            return "REPORT";
        }
        if ("EXECUTION_FAILED".equals(investigationCase.getStatus())) {
            return investigationCase.getExecutionFailureStage() == null
                    ? "FAILED"
                    : investigationCase.getExecutionFailureStage().toUpperCase(
                            Locale.ROOT
                    );
        }

        for (String agentType : List.of("FRAUD", "KYC", "AML", "COMPLIANCE")) {
            Optional<AgentFinding> finding = findings.stream()
                    .filter(item -> agentType.equals(item.getAgentType()))
                    .findFirst();
            if (finding.isEmpty() || "PENDING".equals(finding.get().getStatus())) {
                return agentType;
            }
        }

        return "REPORT";
    }

    private String resolveReference(InvestigationCase investigationCase) {
        if (investigationCase.getTransaction() != null) {
            return investigationCase.getTransaction().getTransactionReference();
        }
        return investigationCase.getTitle();
    }

    private String resolveCustomerName(InvestigationCase investigationCase) {
        if (investigationCase.getCustomer() == null) {
            return "Unknown customer";
        }
        return investigationCase.getCustomer().getFullName();
    }

    private String resolveRecommendation(
            Optional<InvestigationReportEntity> report,
            List<AgentFinding> findings
    ) {
        if (report.isPresent()) {
            String fromReport = readJsonField(
                    report.get().getStructuredJson(),
                    "recommendation"
            );
            if (fromReport != null && !fromReport.isBlank()) {
                return fromReport;
            }
        }

        Optional<AgentFinding> complianceFinding = findings.stream()
                .filter(finding -> "COMPLIANCE".equals(finding.getAgentType()))
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .findFirst();
        if (complianceFinding.isPresent()) {
            String fromFinding = readJsonField(
                    complianceFinding.get().getStructuredJson(),
                    "recommendation"
            );
            if (fromFinding != null && !fromFinding.isBlank()) {
                return fromFinding;
            }
        }

        return "Pending review";
    }

    private Integer resolveConfidencePercent(List<AgentFinding> findings) {
        List<BigDecimal> confidences = findings.stream()
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .map(AgentFinding::getConfidence)
                .filter(Objects::nonNull)
                .toList();
        if (confidences.isEmpty()) {
            return null;
        }

        BigDecimal average = confidences.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(
                        BigDecimal.valueOf(confidences.size()),
                        3,
                        RoundingMode.HALF_UP
                );
        return average.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String readJsonField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    }
            );
            Object value = payload.get(fieldName);
            return value == null ? null : String.valueOf(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private static final class AgentActivityAccumulator {
        private long runningCount;
        private long completedCount;
        private long failedCount;
        private double durationTotal;
        private long durationSamples;

        private void addDuration(double averageDuration, long count) {
            durationTotal += averageDuration * count;
            durationSamples += count;
        }

        private Long averageDurationMs() {
            if (durationSamples == 0) {
                return null;
            }
            return Math.round(durationTotal / durationSamples);
        }
    }
}
