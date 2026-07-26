package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.chat.OpenAiService;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.observability.BankingMetrics;
import com.umeshowl.banking.observability.TestBankingMetrics;
import com.umeshowl.banking.notification.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationReportServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "a0000000-0000-4000-8000-000000000001"
    );
    private static final UUID REPORT_ID = UUID.fromString(
            "a0000000-0000-4000-8000-000000000002"
    );

    private InvestigationCaseService investigationCaseService;
    private AgentFindingRepository agentFindingRepository;
    private AgentFindingCitationRepository citationRepository;
    private ReportContextAssembler contextAssembler;
    private InvestigationPromptBuilder promptBuilder;
    private OpenAiService openAiService;
    private DeterministicInvestigationReportGenerator deterministicGenerator;
    private LlmInvestigationReportMerger llmMerger;
    private InvestigationReportStore reportStore;
    private InvestigationReportProperties properties;
    private BankingMetrics bankingMetrics;
    private InvestigationReportService reportService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        investigationCaseService = mock(InvestigationCaseService.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        citationRepository = mock(AgentFindingCitationRepository.class);
        contextAssembler = new ReportContextAssembler(objectMapper);
        promptBuilder = new InvestigationPromptBuilder(objectMapper);
        openAiService = mock(OpenAiService.class);
        deterministicGenerator = new DeterministicInvestigationReportGenerator();
        llmMerger = new LlmInvestigationReportMerger(
                objectMapper,
                deterministicGenerator
        );
        reportStore = mock(InvestigationReportStore.class);
        properties = new InvestigationReportProperties();
        properties.setMaxRetries(3);
        properties.setRetryDelayMs(1L);
        bankingMetrics = TestBankingMetrics.create();
        reportService = new InvestigationReportService(
                investigationCaseService,
                agentFindingRepository,
                citationRepository,
                contextAssembler,
                promptBuilder,
                openAiService,
                deterministicGenerator,
                llmMerger,
                reportStore,
                properties,
                bankingMetrics,
                mock(NotificationPublisher.class)
        );

        InvestigationCase investigationCase = buildInvestigationCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(citationRepository.findByFinding_IdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(reportStore.save(eq(INVESTIGATION_ID), any(), any()))
                .thenAnswer(invocation -> {
                    InvestigationReport report = invocation.getArgument(1);
                    return new InvestigationReport(
                            REPORT_ID,
                            report.investigationId(),
                            report.metadata(),
                            report.executiveSummary(),
                            report.investigationOverview(),
                            report.customerRiskProfile(),
                            report.fraudAnalysis(),
                            report.kycAnalysis(),
                            report.amlAnalysis(),
                            report.complianceAssessment(),
                            report.supportingEvidence(),
                            report.analystRecommendation(),
                            report.confidenceExplanation(),
                            report.limitations()
                    );
                });
    }

    @Test
    void generatesDeterministicReportWhenLlmIsNotConfigured() {
        stubFindings();
        when(openAiService.isConfigured()).thenReturn(false);

        InvestigationReport report = reportService.generateReport(INVESTIGATION_ID);

        assertEquals(REPORT_ID, report.id());
        assertEquals("1.0.0", report.metadata().promptVersion());
        assertEquals("DETERMINISTIC", report.metadata().generationMode());
        assertEquals("deterministic", report.metadata().modelName());
        assertEquals("ESCALATE", report.analystRecommendation());
        assertEquals(
                "HIGH",
                report.fraudAnalysis().deterministicFacts().get("riskLevel")
        );
        verify(openAiService, never()).generateJsonReply(
                anyString(),
                anyString(),
                anyString(),
                anyDouble()
        );
        verify(reportStore).save(eq(INVESTIGATION_ID), any(), any());
    }

    @Test
    void generatesMergedLlmReportWhenConfigured() {
        stubFindings();
        when(openAiService.isConfigured()).thenReturn(true);
        when(openAiService.generateJsonReply(
                anyString(),
                anyString(),
                anyString(),
                anyDouble()
        )).thenReturn(validLlmJson());
        when(reportStore.save(eq(INVESTIGATION_ID), any(), anyString()))
                .thenAnswer(invocation -> {
                    InvestigationReport report = invocation.getArgument(1);
                    return new InvestigationReport(
                            REPORT_ID,
                            report.investigationId(),
                            report.metadata(),
                            report.executiveSummary(),
                            report.investigationOverview(),
                            report.customerRiskProfile(),
                            report.fraudAnalysis(),
                            report.kycAnalysis(),
                            report.amlAnalysis(),
                            report.complianceAssessment(),
                            report.supportingEvidence(),
                            report.analystRecommendation(),
                            report.confidenceExplanation(),
                            report.limitations()
                    );
                });

        InvestigationReport report = reportService.generateReport(INVESTIGATION_ID);

        assertEquals("LLM", report.metadata().generationMode());
        assertEquals("Executive summary text", report.executiveSummary());
        assertEquals("Fraud narrative", report.fraudAnalysis().narrative());
        assertEquals("ESCALATE", report.analystRecommendation());
        assertEquals(
                80,
                report.fraudAnalysis().deterministicFacts().get("score")
        );
    }

    @Test
    void fallsBackToDeterministicReportAfterLlmFailure() {
        stubFindings();
        when(openAiService.isConfigured()).thenReturn(true);
        when(openAiService.generateJsonReply(
                anyString(),
                anyString(),
                anyString(),
                anyDouble()
        )).thenThrow(new IllegalStateException("persistent failure"));

        InvestigationReport report = reportService.generateReport(INVESTIGATION_ID);

        assertEquals("DETERMINISTIC", report.metadata().generationMode());
        assertEquals("ESCALATE", report.analystRecommendation());
        verify(openAiService, times(3)).generateJsonReply(
                anyString(),
                anyString(),
                anyString(),
                anyDouble()
        );
        verify(reportStore).save(eq(INVESTIGATION_ID), any(), any());
        assertEquals(1.0, bankingMetrics.reportFallbackTotal());
    }

    @Test
    void recommendationMatchesComplianceFinding() {
        stubFindings();
        when(openAiService.isConfigured()).thenReturn(true);
        when(openAiService.generateJsonReply(
                anyString(),
                anyString(),
                anyString(),
                anyDouble()
        )).thenReturn(validLlmJsonWithWrongRecommendation());

        InvestigationReport report = reportService.generateReport(INVESTIGATION_ID);

        assertEquals("ESCALATE", report.analystRecommendation());
    }

    @Test
    void handlesMissingEvidenceAndMissingFindings() {
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of());
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
        when(openAiService.isConfigured()).thenReturn(false);

        InvestigationReport report = reportService.generateReport(INVESTIGATION_ID);

        assertEquals("REVIEW", report.analystRecommendation());
        assertEquals(1, report.supportingEvidence().size());
        assertEquals(
                "No supporting documentation was retrieved.",
                report.supportingEvidence().getFirst().narrative()
        );
        assertTrue(report.fraudAnalysis().narrative().contains("No completed fraud"));
    }

    @Test
    void loadsLatestStoredReport() {
        InvestigationReport stored = deterministicGenerator.generate(
                INVESTIGATION_ID,
                contextAssembler.assemble(
                        buildInvestigationCase(),
                        Map.of(
                                "COMPLIANCE",
                                completedFinding(
                                        "COMPLIANCE",
                                        "HIGH",
                                        """
                                        {"overallScore":75,"recommendation":"ESCALATE"}
                                        """
                                )
                        ),
                        Map.of()
                ),
                new InvestigationReportMetadata(
                        "1.0.0",
                        OffsetDateTime.now(ZoneOffset.UTC),
                        "deterministic",
                        10L,
                        "DETERMINISTIC"
                )
        );
        InvestigationReport persisted = new InvestigationReport(
                REPORT_ID,
                stored.investigationId(),
                stored.metadata(),
                stored.executiveSummary(),
                stored.investigationOverview(),
                stored.customerRiskProfile(),
                stored.fraudAnalysis(),
                stored.kycAnalysis(),
                stored.amlAnalysis(),
                stored.complianceAssessment(),
                stored.supportingEvidence(),
                stored.analystRecommendation(),
                stored.confidenceExplanation(),
                stored.limitations()
        );
        when(reportStore.findLatest(INVESTIGATION_ID))
                .thenReturn(Optional.of(persisted));

        InvestigationReport report = reportService.getLatestReport(INVESTIGATION_ID);

        assertEquals(REPORT_ID, report.id());
        assertEquals("ESCALATE", report.analystRecommendation());
    }

    private void stubFindings() {
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of(completedFinding(
                "FRAUD",
                "HIGH",
                """
                {"fraudScore":80,"recommendation":"ESCALATE","triggeredIndicators":[{"type":"FLAGGED_TRANSACTION","explanation":"Transaction flagged"}]}
                """
        )));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "KYC"
        )).thenReturn(List.of(completedFinding(
                "KYC",
                "MEDIUM",
                """
                {"kycScore":40,"recommendation":"REVIEW","triggeredIndicators":[]}
                """
        )));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "AML"
        )).thenReturn(List.of(completedFinding(
                "AML",
                "LOW",
                """
                {"amlScore":20,"recommendation":"APPROVE","triggeredIndicators":[]}
                """
        )));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "COMPLIANCE"
        )).thenReturn(List.of(completedFinding(
                "COMPLIANCE",
                "HIGH",
                """
                {"overallScore":75,"recommendation":"ESCALATE","contributingFindings":[]}
                """
        )));
    }

    private InvestigationCase buildInvestigationCase() {
        MockCustomer customer = new MockCustomer();
        customer.setFullName("Jane Doe");
        customer.setAccountNumber("ACC-001");
        customer.setRiskRating("HIGH");
        customer.setKycStatus("COMPLETE");
        customer.setPepStatus("NONE");
        customer.setOccupation("Engineer");
        customer.setSourceOfFunds("Salary");
        customer.setCountryOfResidence("US");

        MockTransaction transaction = new MockTransaction();
        transaction.setTransactionReference("TX-001");
        transaction.setAmount(new BigDecimal("15000.00"));
        transaction.setCurrency("USD");
        transaction.setChannel("WIRE");
        transaction.setFlagged(true);
        transaction.setRiskScore(new BigDecimal("82"));

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setTitle("Test case");
        investigationCase.setDescription("Test description");
        investigationCase.setCaseType("FRAUD");
        investigationCase.setStatus("OPEN");
        investigationCase.setPriority("HIGH");
        investigationCase.setCustomer(customer);
        investigationCase.setTransaction(transaction);
        return investigationCase;
    }

    private AgentFinding completedFinding(
            String agentType,
            String riskLevel,
            String structuredJson
    ) {
        AgentFinding finding = new AgentFinding();
        finding.setId(UUID.randomUUID());
        finding.setAgentType(agentType);
        finding.setStatus("COMPLETE");
        finding.setRiskLevel(riskLevel);
        finding.setConfidence(new BigDecimal("0.800"));
        finding.setSummary(agentType + " summary");
        finding.setStructuredJson(structuredJson);
        finding.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return finding;
    }

    private String validLlmJson() {
        return """
                {
                  "executiveSummary": "Executive summary text",
                  "investigationOverview": {
                    "title": "Overview",
                    "narrative": "Overview narrative"
                  },
                  "customerRiskProfile": {
                    "title": "Customer",
                    "narrative": "Customer narrative"
                  },
                  "fraudAnalysis": {
                    "title": "Fraud",
                    "narrative": "Fraud narrative"
                  },
                  "kycAnalysis": {
                    "title": "KYC",
                    "narrative": "KYC narrative"
                  },
                  "amlAnalysis": {
                    "title": "AML",
                    "narrative": "AML narrative"
                  },
                  "complianceAssessment": {
                    "title": "Compliance",
                    "narrative": "Compliance narrative"
                  },
                  "supportingEvidence": [],
                  "limitations": "Advisory only",
                  "confidenceExplanation": "Based on deterministic findings"
                }
                """;
    }

    private String validLlmJsonWithWrongRecommendation() {
        return validLlmJson().replace(
                "\"confidenceExplanation\"",
                "\"analystRecommendation\":\"APPROVE\",\"confidenceExplanation\""
        );
    }
}
