package com.umeshowl.banking.investigation.report;

import com.umeshowl.banking.chat.OpenAiService;
import com.umeshowl.banking.observability.BankingMetrics;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.notification.NotificationPublisher;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestigationReportService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationReportService.class
    );

    private static final List<String> AGENT_TYPES = List.of(
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE"
    );

    private static final String MODE_DETERMINISTIC = "DETERMINISTIC";
    private static final String MODE_LLM = "LLM";

    private final InvestigationCaseService investigationCaseService;
    private final AgentFindingRepository agentFindingRepository;
    private final AgentFindingCitationRepository citationRepository;
    private final ReportContextAssembler contextAssembler;
    private final InvestigationPromptBuilder promptBuilder;
    private final OpenAiService openAiService;
    private final DeterministicInvestigationReportGenerator deterministicGenerator;
    private final LlmInvestigationReportMerger llmMerger;
    private final InvestigationReportStore reportStore;
    private final InvestigationReportProperties properties;
    private final BankingMetrics bankingMetrics;
    private final NotificationPublisher notificationPublisher;

    public InvestigationReportService(
            InvestigationCaseService investigationCaseService,
            AgentFindingRepository agentFindingRepository,
            AgentFindingCitationRepository citationRepository,
            ReportContextAssembler contextAssembler,
            InvestigationPromptBuilder promptBuilder,
            OpenAiService openAiService,
            DeterministicInvestigationReportGenerator deterministicGenerator,
            LlmInvestigationReportMerger llmMerger,
            InvestigationReportStore reportStore,
            InvestigationReportProperties properties,
            BankingMetrics bankingMetrics,
            NotificationPublisher notificationPublisher
    ) {
        this.investigationCaseService = investigationCaseService;
        this.agentFindingRepository = agentFindingRepository;
        this.citationRepository = citationRepository;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.openAiService = openAiService;
        this.deterministicGenerator = deterministicGenerator;
        this.llmMerger = llmMerger;
        this.reportStore = reportStore;
        this.properties = properties;
        this.bankingMetrics = bankingMetrics;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public InvestigationReport generateReport(UUID investigationId) {
        Timer.Sample reportTimer = bankingMetrics.startReportGenerationTimer();
        long startedAt = System.currentTimeMillis();

        try {
            InvestigationCase investigationCase =
                    investigationCaseService.getCase(investigationId);
            Map<String, AgentFinding> findingsByAgent =
                    loadCompletedFindings(investigationId);
            Map<String, List<AgentFindingCitation>> citationsByAgent =
                    loadCitations(findingsByAgent);
            ReportSourceData sourceData = contextAssembler.assemble(
                    investigationCase,
                    findingsByAgent,
                    citationsByAgent
            );

            String rawLlmResponse = null;
            String generationMode = MODE_DETERMINISTIC;
            String modelName = "deterministic";
            boolean usedFallback = false;

            InvestigationReport report;
            if (openAiService.isConfigured()) {
                try {
                    rawLlmResponse = generateWithRetry(
                            promptBuilder.buildSystemPrompt(
                                    properties.getPromptVersion()
                            ),
                            promptBuilder.buildUserPrompt(sourceData)
                    );
                    generationMode = MODE_LLM;
                    modelName = properties.getModel();
                    report = llmMerger.merge(
                            investigationId,
                            sourceData,
                            rawLlmResponse,
                            buildMetadata(
                                    startedAt,
                                    generationMode,
                                    modelName
                            )
                    );
                } catch (RuntimeException exception) {
                    log.warn(
                            "investigation_report_llm_failed message={}",
                            exception.getMessage()
                    );
                    usedFallback = true;
                    report = deterministicGenerator.generate(
                            investigationId,
                            sourceData,
                            buildMetadata(
                                    startedAt,
                                    MODE_DETERMINISTIC,
                                    "deterministic"
                            )
                    );
                }
            } else {
                log.info(
                        "investigation_report_generation mode={} reason=openai_not_configured",
                        MODE_DETERMINISTIC
                );
                report = deterministicGenerator.generate(
                        investigationId,
                        sourceData,
                        buildMetadata(
                                startedAt,
                                MODE_DETERMINISTIC,
                                "deterministic"
                        )
                );
            }

            InvestigationReport savedReport = reportStore.save(
                    investigationId,
                    report,
                    rawLlmResponse
            );
            bankingMetrics.recordReportGenerated(generationMode, reportTimer);
            if (usedFallback) {
                bankingMetrics.recordReportFallback();
                notificationPublisher.notifyOpenAiFallback(investigationId);
            }
            log.info(
                    "investigation_report_generated mode={} fallback={}",
                    generationMode,
                    usedFallback
            );
            return savedReport;
        } catch (RuntimeException exception) {
            bankingMetrics.recordReportFailure(reportTimer);
            log.error(
                    "investigation_report_generation_failed message={}",
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public InvestigationReport getLatestReport(UUID investigationId) {
        return reportStore.findLatest(investigationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No investigation report found for case "
                                        + investigationId
                        )
                );
    }

    String generateWithRetry(String systemPrompt, String userPrompt) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                return openAiService.generateJsonReply(
                        systemPrompt,
                        userPrompt,
                        properties.getModel(),
                        properties.getTemperature()
                );
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn(
                        "investigation_report_generation_attempt_failed attempt={} maxAttempts={} message={}",
                        attempt,
                        properties.getMaxRetries(),
                        exception.getMessage()
                );
                if (attempt < properties.getMaxRetries()) {
                    sleepBeforeRetry();
                }
            }
        }

        throw new IllegalStateException(
                "Investigation report generation failed after "
                        + properties.getMaxRetries()
                        + " attempts",
                lastFailure
        );
    }

    private InvestigationReportMetadata buildMetadata(
            long startedAt,
            String generationMode,
            String modelName
    ) {
        return new InvestigationReportMetadata(
                properties.getPromptVersion(),
                OffsetDateTime.now(ZoneOffset.UTC),
                modelName,
                System.currentTimeMillis() - startedAt,
                generationMode
        );
    }

    private Map<String, AgentFinding> loadCompletedFindings(UUID investigationId) {
        Map<String, AgentFinding> findings = new LinkedHashMap<>();
        for (String agentType : AGENT_TYPES) {
            agentFindingRepository
                    .findByInvestigationCase_IdAndAgentType(
                            investigationId,
                            agentType
                    )
                    .stream()
                    .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                    .max(Comparator.comparing(AgentFinding::getCreatedAt))
                    .ifPresent(finding -> findings.put(agentType, finding));
        }
        return findings;
    }

    private Map<String, List<AgentFindingCitation>> loadCitations(
            Map<String, AgentFinding> findingsByAgent
    ) {
        Map<String, List<AgentFindingCitation>> citationsByAgent =
                new LinkedHashMap<>();
        findingsByAgent.forEach((agentType, finding) ->
                citationsByAgent.put(
                        agentType,
                        citationRepository.findByFinding_IdOrderByCreatedAtAsc(
                                finding.getId()
                        )
                )
        );
        return citationsByAgent;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(properties.getRetryDelayMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Investigation report retry interrupted",
                    exception
            );
        }
    }
}
