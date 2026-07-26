package com.umeshowl.banking.operations;

import com.umeshowl.banking.dashboard.OperationsDashboardService;
import com.umeshowl.banking.dashboard.dto.OperationsDashboardResponse;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.observability.BankingMetrics;
import com.umeshowl.banking.observability.InvestigationExecutionHealthIndicator;
import com.umeshowl.banking.observability.OpenAiConfigurationHealthIndicator;
import com.umeshowl.banking.operations.dto.*;
import com.umeshowl.banking.screening.TransactionScreeningResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OperationsCenterService {

    private final DataSource dataSource;
    private final OpenAiConfigurationHealthIndicator openAiHealthIndicator;
    private final InvestigationExecutionHealthIndicator executionHealthIndicator;
    private final InvestigationCaseRepository investigationCaseRepository;
    private final TransactionScreeningResultRepository screeningResultRepository;
    private final OperationsDashboardService operationsDashboardService;
    private final BankingMetrics bankingMetrics;
    private final UUID defaultProjectId;

    public OperationsCenterService(
            DataSource dataSource,
            OpenAiConfigurationHealthIndicator openAiHealthIndicator,
            InvestigationExecutionHealthIndicator executionHealthIndicator,
            InvestigationCaseRepository investigationCaseRepository,
            TransactionScreeningResultRepository screeningResultRepository,
            OperationsDashboardService operationsDashboardService,
            BankingMetrics bankingMetrics,
            @Value("${investigation.auto-create.default-project-id}")
            UUID defaultProjectId
    ) {
        this.dataSource = dataSource;
        this.openAiHealthIndicator = openAiHealthIndicator;
        this.executionHealthIndicator = executionHealthIndicator;
        this.investigationCaseRepository = investigationCaseRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.operationsDashboardService = operationsDashboardService;
        this.bankingMetrics = bankingMetrics;
        this.defaultProjectId = defaultProjectId;
    }

    @Transactional(readOnly = true)
    public OperationsCenterResponse loadOperationsCenter(UUID projectId) {
        UUID resolvedProjectId = projectId == null ? defaultProjectId : projectId;
        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS);

        OperationsDashboardResponse dashboard =
                operationsDashboardService.loadOperationsDashboard(resolvedProjectId);

        return new OperationsCenterResponse(
                buildPlatformHealth(),
                buildInvestigationMetrics(resolvedProjectId, startOfToday),
                dashboard.agentActivity(),
                buildRecentErrors(resolvedProjectId),
                (long) bankingMetrics.executionFailureTotal(),
                (long) bankingMetrics.reportFallbackTotal(),
                (long) bankingMetrics.reportFailureTotal(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private PlatformHealthSummary buildPlatformHealth() {
        List<HealthComponentStatus> components = List.of(
                buildBackendHealth(),
                buildDatabaseHealth(),
                buildSseHealth(),
                buildOpenAiHealth()
        );

        String overallStatus = resolveOverallStatus(components);
        return new PlatformHealthSummary(overallStatus, components);
    }

    private HealthComponentStatus buildBackendHealth() {
        Health executionHealth = executionHealthIndicator.health();
        String status = executionHealth.getStatus().getCode();
        Object detail = executionHealth.getDetails().get("status");
        return new HealthComponentStatus(
                "Backend",
                status,
                detail == null
                        ? "Application services are running"
                        : String.valueOf(detail)
        );
    }

    private HealthComponentStatus buildDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return new HealthComponentStatus(
                        "Database",
                        "UP",
                        "PostgreSQL connection is healthy"
                );
            }
            return new HealthComponentStatus(
                    "Database",
                    "DOWN",
                    "Database connection validation failed"
            );
        } catch (Exception exception) {
            return new HealthComponentStatus(
                    "Database",
                    "DOWN",
                    "Unable to connect to PostgreSQL"
            );
        }
    }

    private HealthComponentStatus buildSseHealth() {
        return new HealthComponentStatus(
                "SSE",
                "UP",
                "Investigation and simulation live streams are available"
        );
    }

    private HealthComponentStatus buildOpenAiHealth() {
        Health health = openAiHealthIndicator.health();
        Object configured = health.getDetails().get("configured");
        Object status = health.getDetails().get("status");
        boolean isConfigured = Boolean.TRUE.equals(configured);
        return new HealthComponentStatus(
                "OpenAI",
                isConfigured ? "UP" : "DEGRADED",
                status == null
                        ? "OpenAI configuration status unavailable"
                        : String.valueOf(status)
        );
    }

    private String resolveOverallStatus(List<HealthComponentStatus> components) {
        boolean hasDown = components.stream()
                .anyMatch(component -> "DOWN".equals(component.status()));
        if (hasDown) {
            return "DOWN";
        }

        boolean hasDegraded = components.stream()
                .anyMatch(component -> "DEGRADED".equals(component.status()));
        if (hasDegraded) {
            return "DEGRADED";
        }

        return "UP";
    }

    private InvestigationMetricsSummary buildInvestigationMetrics(
            UUID projectId,
            OffsetDateTime startOfToday
    ) {
        long transactionsProcessedToday =
                screeningResultRepository.countByScreenedAtGreaterThanEqual(
                        startOfToday
                );
        long investigationsCreatedToday =
                investigationCaseRepository
                        .countByProject_IdAndCreatedAtGreaterThanEqual(
                                projectId,
                                startOfToday
                        );
        long criticalInvestigations =
                investigationCaseRepository.countByProject_IdAndPriority(
                        projectId,
                        "CRITICAL"
                );
        long awaitingAnalystReview =
                investigationCaseRepository.countByProject_IdAndStatus(
                        projectId,
                        "AWAITING_REVIEW"
                );
        long closedInvestigations =
                investigationCaseRepository.countByProject_IdAndStatus(
                        projectId,
                        "CLOSED"
                );
        long failedInvestigations =
                investigationCaseRepository.countByProject_IdAndStatus(
                        projectId,
                        "EXECUTION_FAILED"
                );

        return new InvestigationMetricsSummary(
                transactionsProcessedToday,
                investigationsCreatedToday,
                criticalInvestigations,
                awaitingAnalystReview,
                closedInvestigations,
                failedInvestigations
        );
    }

    private List<OperationsErrorEntry> buildRecentErrors(UUID projectId) {
        List<OperationsErrorEntry> errors = new ArrayList<>();

        for (InvestigationCase investigationCase :
                investigationCaseRepository
                        .findTop10ByProject_IdAndStatusOrderByUpdatedAtDesc(
                                projectId,
                                "EXECUTION_FAILED"
                        )) {
            errors.add(new OperationsErrorEntry(
                    "EXECUTION_FAILED",
                    investigationCase.getExecutionFailureMessage() == null
                            ? "Investigation execution failed"
                            : investigationCase.getExecutionFailureMessage(),
                    investigationCase.getExecutionFailureStage() == null
                            ? "Investigation pipeline"
                            : investigationCase.getExecutionFailureStage()
                                    .toUpperCase(Locale.ROOT),
                    investigationCase.getId(),
                    investigationCase.getExecutionFailureAt() == null
                            ? investigationCase.getUpdatedAt()
                            : investigationCase.getExecutionFailureAt()
            ));
        }

        long fallbackTotal = (long) bankingMetrics.reportFallbackTotal();
        if (fallbackTotal > 0) {
            errors.add(new OperationsErrorEntry(
                    "OPENAI_FALLBACK",
                    "Report generation used deterministic fallback "
                            + fallbackTotal
                            + " time(s)",
                    "Report Agent",
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
        }

        long reportFailures = (long) bankingMetrics.reportFailureTotal();
        if (reportFailures > 0) {
            errors.add(new OperationsErrorEntry(
                    "REPORT_FAILURE",
                    "Report generation failed "
                            + reportFailures
                            + " time(s) since startup",
                    "Report Agent",
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
        }

        return errors.stream()
                .sorted(Comparator.comparing(
                        OperationsErrorEntry::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(20)
                .toList();
    }
}
