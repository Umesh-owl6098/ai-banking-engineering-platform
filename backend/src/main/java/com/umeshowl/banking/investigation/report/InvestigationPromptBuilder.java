package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InvestigationPromptBuilder {

    private final ObjectMapper objectMapper;

    public InvestigationPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildSystemPrompt(String promptVersion) {
        return """
                You are an advisory banking investigation report writer.
                Prompt version: %s.

                Rules:
                1. Write clearly for compliance analysts.
                2. Use ONLY the supplied investigation, findings, and evidence data.
                3. NEVER change, recalculate, or contradict deterministic scores, risk levels, or recommendations.
                4. Treat deterministic outputs as authoritative facts.
                5. Your role is summarization, explanation, and analyst-friendly language only.
                6. Cite supporting policy excerpts when relevant.
                7. Return valid JSON only with these exact keys:
                   executiveSummary,
                   investigationOverview,
                   customerRiskProfile,
                   fraudAnalysis,
                   kycAnalysis,
                   amlAnalysis,
                   complianceAssessment,
                   supportingEvidence,
                   confidenceExplanation,
                   limitations
                8. Do NOT include analystRecommendation in the JSON response.
                9. Each section value (except supportingEvidence) must be an object with:
                   title, narrative
                10. supportingEvidence must be an array of objects with:
                    title, narrative
                11. executiveSummary must be at most 200 words.
                12. Do not include markdown fences or extra keys.
                """.formatted(promptVersion);
    }

    public String buildUserPrompt(ReportSourceData sourceData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("investigation", sourceData.investigation());
        payload.put("customer", sourceData.customer());
        payload.put("transaction", sourceData.transaction());
        payload.put("fraudFinding", sourceData.fraudFinding());
        payload.put("kycFinding", sourceData.kycFinding());
        payload.put("amlFinding", sourceData.amlFinding());
        payload.put("complianceFinding", sourceData.complianceFinding());
        payload.put("evidence", sourceData.evidence());
        payload.put("instructions", Map.of(
                "advisoryOnly", true,
                "doNotChangeScores", true,
                "doNotChangeRecommendations", true,
                "doNotChangeRiskLevels", true,
                "usePolicyExcerpts", true
        ));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize investigation report prompt",
                    exception
            );
        }
    }

    public String expectedJsonSchemaDescription() {
        return """
                {
                  "executiveSummary": "string (max 200 words)",
                  "investigationOverview": {"title":"string","narrative":"string"},
                  "customerRiskProfile": {"title":"string","narrative":"string"},
                  "fraudAnalysis": {"title":"string","narrative":"string"},
                  "kycAnalysis": {"title":"string","narrative":"string"},
                  "amlAnalysis": {"title":"string","narrative":"string"},
                  "complianceAssessment": {"title":"string","narrative":"string"},
                  "supportingEvidence": [{"title":"string","narrative":"string"}],
                  "confidenceExplanation": "string",
                  "limitations": "string"
                }
                """;
    }
}
