package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmInvestigationReportMergerTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "a0000000-0000-4000-8000-000000000004"
    );

    private LlmInvestigationReportMerger merger;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        merger = new LlmInvestigationReportMerger(
                objectMapper,
                new DeterministicInvestigationReportGenerator()
        );
    }

    @Test
    void parsesValidJsonResponse() {
        Map<String, Object> parsed = merger.parseResponse(validLlmJson());

        assertEquals("Executive summary text", parsed.get("executiveSummary"));
        assertTrue(parsed.containsKey("fraudAnalysis"));
    }

    @Test
    void rejectsInvalidJsonResponse() {
        assertThrows(
                IllegalStateException.class,
                () -> merger.parseResponse("not-json")
        );
    }

    @Test
    void preservesDeterministicRecommendationAndFacts() {
        InvestigationReport report = merger.merge(
                INVESTIGATION_ID,
                sampleSource(),
                validLlmJson(),
                metadata()
        );

        assertEquals("ESCALATE", report.analystRecommendation());
        assertEquals("Fraud narrative", report.fraudAnalysis().narrative());
        assertEquals(
                80,
                report.fraudAnalysis().deterministicFacts().get("score")
        );
        assertEquals(
                "HIGH",
                report.fraudAnalysis().deterministicFacts().get("riskLevel")
        );
    }

    private ReportSourceData sampleSource() {
        return new ReportSourceData(
                Map.of("title", "Test case", "caseType", "FRAUD"),
                Map.of("available", false),
                Map.of("available", false),
                Map.of(
                        "available", true,
                        "score", 80,
                        "riskLevel", "HIGH",
                        "recommendation", "ESCALATE"
                ),
                Map.of("available", false),
                Map.of("available", false),
                Map.of(
                        "available", true,
                        "recommendation", "ESCALATE"
                ),
                List.of()
        );
    }

    private InvestigationReportMetadata metadata() {
        return new InvestigationReportMetadata(
                "1.0.0",
                OffsetDateTime.now(ZoneOffset.UTC),
                "gpt-4.1-mini",
                12L,
                "LLM"
        );
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
}
