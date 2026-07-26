package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationPromptBuilderTest {

    private InvestigationPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new InvestigationPromptBuilder(new ObjectMapper());
    }

    @Test
    void systemPromptIncludesPromptVersionAndAdvisoryRules() {
        String prompt = promptBuilder.buildSystemPrompt("1.0.0");

        assertTrue(prompt.contains("Prompt version: 1.0.0"));
        assertTrue(prompt.contains("advisory"));
        assertTrue(prompt.contains("NEVER change"));
        assertTrue(prompt.contains("customerRiskProfile"));
        assertTrue(prompt.contains("complianceAssessment"));
        assertTrue(prompt.contains("Do NOT include analystRecommendation"));
    }

    @Test
    void userPromptUsesReportSourceDataWithoutInternalEntities() {
        ReportSourceData sourceData = new ReportSourceData(
                Map.of(
                        "title", "Flagged transfer review",
                        "caseType", "FRAUD"
                ),
                Map.of(
                        "available", true,
                        "name", "Jane Doe",
                        "riskRating", "HIGH"
                ),
                Map.of("available", false),
                Map.of(
                        "available", true,
                        "agentType", "FRAUD",
                        "score", 80,
                        "recommendation", "ESCALATE",
                        "riskLevel", "HIGH"
                ),
                Map.of("available", false),
                Map.of("available", false),
                Map.of(
                        "available", true,
                        "agentType", "COMPLIANCE",
                        "recommendation", "ESCALATE"
                ),
                List.of()
        );

        String prompt = promptBuilder.buildUserPrompt(sourceData);

        assertTrue(prompt.contains("Flagged transfer review"));
        assertTrue(prompt.contains("fraudFinding"));
        assertTrue(prompt.contains("complianceFinding"));
        assertTrue(prompt.contains("doNotChangeScores"));
        assertTrue(prompt.contains("doNotChangeRecommendations"));
        assertFalse(prompt.contains("AgentFinding"));
        assertFalse(prompt.contains("InvestigationCase"));
    }

    @Test
    void expectedJsonSchemaIncludesAllSections() {
        String schema = promptBuilder.expectedJsonSchemaDescription();

        assertTrue(schema.contains("customerRiskProfile"));
        assertTrue(schema.contains("complianceAssessment"));
        assertTrue(schema.contains("supportingEvidence"));
    }
}
