package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.fraud.FraudAnalysisResult;
import com.umeshowl.banking.investigation.fraud.FraudIndicator;
import com.umeshowl.banking.investigation.fraud.FraudIndicatorType;
import com.umeshowl.banking.investigation.fraud.FraudRiskLevel;
import com.umeshowl.banking.investigation.kyc.KycAnalysisResult;
import com.umeshowl.banking.investigation.kyc.KycIndicator;
import com.umeshowl.banking.investigation.kyc.KycIndicatorType;
import com.umeshowl.banking.investigation.kyc.KycRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentFindingServiceTest {

    private static final UUID CASE_ID = UUID.fromString(
            "50000000-0000-4000-8000-000000000001"
    );

    private InvestigationCaseService investigationCaseService;
    private AgentFindingRepository agentFindingRepository;
    private ObjectMapper objectMapper;
    private AgentFindingService agentFindingService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        AgentFindingCitationRepository citationRepository =
                mock(AgentFindingCitationRepository.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        agentFindingService = new AgentFindingService(
                investigationCaseService,
                agentFindingRepository,
                citationRepository,
                objectMapper
        );

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(CASE_ID);
        when(investigationCaseService.getCase(CASE_ID)).thenReturn(
                investigationCase
        );
        when(agentFindingRepository.save(any(AgentFinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persistsCompleteFraudFindingWithStructuredEvidence()
            throws Exception {
        AgentFinding finding = agentFindingService.persistFraudAnalysis(
                analysis(
                        FraudRiskLevel.HIGH,
                        List.of(indicator(
                                FraudIndicatorType.FLAGGED_TRANSACTION
                        ))
                )
        );

        JsonNode structured = objectMapper.readTree(
                finding.getStructuredJson()
        );

        assertEquals("FRAUD", finding.getAgentType());
        assertEquals("COMPLETE", finding.getStatus());
        assertEquals("HIGH", finding.getRiskLevel());
        assertEquals(new BigDecimal("0.800"), finding.getConfidence());
        assertEquals("ESCALATE", structured.get("recommendation")
                .asText());
        assertEquals(1, structured.get("triggeredIndicators").size());
        assertTrue(finding.getCitations().isEmpty());
        verify(agentFindingRepository).save(finding);
    }

    @Test
    void mapsRecommendationsAndConfidenceForEveryRiskLevel() {
        assertFindingMapping(
                FraudRiskLevel.LOW,
                "APPROVE",
                "0.350"
        );
        assertFindingMapping(
                FraudRiskLevel.MEDIUM,
                "REVIEW",
                "0.600"
        );
        assertFindingMapping(
                FraudRiskLevel.HIGH,
                "ESCALATE",
                "0.800"
        );
        assertFindingMapping(
                FraudRiskLevel.CRITICAL,
                "ESCALATE",
                "0.950"
        );
    }

    @Test
    void preservesMultipleIndicatorsAndEvidenceValues() throws Exception {
        AgentFinding finding = agentFindingService.persistFraudAnalysis(
                analysis(
                        FraudRiskLevel.CRITICAL,
                        List.of(
                                indicator(
                                        FraudIndicatorType.FLAGGED_TRANSACTION
                                ),
                                indicator(
                                        FraudIndicatorType.STRUCTURING
                                )
                        )
                )
        );

        JsonNode indicators = objectMapper.readTree(
                finding.getStructuredJson()
        ).get("triggeredIndicators");

        assertEquals(2, indicators.size());
        assertEquals(
                "FLAGGED_TRANSACTION",
                indicators.get(0).get("type").asText()
        );
        assertEquals(
                "STRUCTURING",
                indicators.get(1).get("type").asText()
        );
        assertFalse(
                indicators.get(0).get("evidenceValues").isEmpty()
        );
    }

    @Test
    void persistsZeroIndicatorsAsAnEmptyStructuredArray()
            throws Exception {
        AgentFinding finding = agentFindingService.persistFraudAnalysis(
                analysis(FraudRiskLevel.LOW, List.of())
        );

        JsonNode structured = objectMapper.readTree(
                finding.getStructuredJson()
        );

        assertTrue(structured.get("triggeredIndicators").isEmpty());
        assertEquals("APPROVE", structured.get("recommendation")
                .asText());
    }

    @Test
    void persistsCompleteKycFindingWithRecommendation() throws Exception {
        KycAnalysisResult analysis = new KycAnalysisResult(
                CASE_ID, null, null, 60, KycRiskLevel.HIGH,
                "KYC review required",
                List.of(new KycIndicator(
                        KycIndicatorType.PEP_CUSTOMER,
                        20,
                        "Customer is a PEP",
                        Map.of("pepStatus", "PEP")
                )),
                OffsetDateTime.of(
                        2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC
                )
        );

        AgentFinding finding = agentFindingService.persistKycAnalysis(
                analysis
        );
        JsonNode structured = objectMapper.readTree(
                finding.getStructuredJson()
        );

        assertEquals("KYC", finding.getAgentType());
        assertEquals("COMPLETE", finding.getStatus());
        assertEquals(new BigDecimal("0.800"), finding.getConfidence());
        assertEquals("ESCALATE", structured.get("recommendation")
                .asText());
        assertEquals(1, structured.get("triggeredIndicators").size());
    }

    private void assertFindingMapping(
            FraudRiskLevel riskLevel,
            String recommendation,
            String confidence
    ) {
        AgentFinding finding = agentFindingService.persistFraudAnalysis(
                analysis(riskLevel, List.of())
        );

        assertEquals(
                new BigDecimal(confidence),
                finding.getConfidence()
        );
        assertTrue(
                finding.getStructuredJson().contains(
                        "\"recommendation\":\""
                                + recommendation
                                + "\""
                )
        );
    }

    private FraudAnalysisResult analysis(
            FraudRiskLevel riskLevel,
            List<FraudIndicator> indicators
    ) {
        return new FraudAnalysisResult(
                CASE_ID,
                UUID.fromString(
                        "50000000-0000-4000-8000-000000000002"
                ),
                UUID.fromString(
                        "50000000-0000-4000-8000-000000000003"
                ),
                80,
                riskLevel,
                "Deterministic fraud analysis",
                indicators,
                OffsetDateTime.of(
                        2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC
                )
        );
    }

    private FraudIndicator indicator(FraudIndicatorType type) {
        return new FraudIndicator(
                type,
                20,
                "Indicator explanation",
                Map.of("amount", new BigDecimal("100.00")),
                List.of(
                        UUID.fromString(
                                "50000000-0000-4000-8000-000000000003"
                        )
                )
        );
    }
}
