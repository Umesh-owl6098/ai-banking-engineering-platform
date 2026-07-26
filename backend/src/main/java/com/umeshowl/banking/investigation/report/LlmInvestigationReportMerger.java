package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LlmInvestigationReportMerger {

    private final ObjectMapper objectMapper;
    private final DeterministicInvestigationReportGenerator deterministicGenerator;

    public LlmInvestigationReportMerger(
            ObjectMapper objectMapper,
            DeterministicInvestigationReportGenerator deterministicGenerator
    ) {
        this.objectMapper = objectMapper;
        this.deterministicGenerator = deterministicGenerator;
    }

    public InvestigationReport merge(
            UUID investigationId,
            ReportSourceData source,
            String rawLlmResponse,
            InvestigationReportMetadata metadata
    ) {
        Map<String, Object> parsed = parseResponse(rawLlmResponse);
        InvestigationReport deterministic = deterministicGenerator.generate(
                investigationId,
                source,
                metadata
        );

        return new InvestigationReport(
                null,
                investigationId,
                metadata,
                mergeExecutiveSummary(parsed, deterministic.executiveSummary()),
                mergeSection(
                        parsed.get("investigationOverview"),
                        deterministic.investigationOverview()
                ),
                mergeSection(
                        parsed.get("customerRiskProfile"),
                        deterministic.customerRiskProfile()
                ),
                mergeSection(
                        parsed.get("fraudAnalysis"),
                        deterministic.fraudAnalysis()
                ),
                mergeSection(
                        parsed.get("kycAnalysis"),
                        deterministic.kycAnalysis()
                ),
                mergeSection(
                        parsed.get("amlAnalysis"),
                        deterministic.amlAnalysis()
                ),
                mergeSection(
                        parsed.get("complianceAssessment"),
                        deterministic.complianceAssessment()
                ),
                mergeSupportingEvidence(parsed, deterministic.supportingEvidence()),
                deterministic.analystRecommendation(),
                mergeText(
                        parsed.get("confidenceExplanation"),
                        deterministic.confidenceExplanation()
                ),
                mergeText(parsed.get("limitations"), deterministic.limitations())
        );
    }

    Map<String, Object> parseResponse(String rawResponse) {
        try {
            return objectMapper.readValue(
                    rawResponse,
                    new TypeReference<>() {
                    }
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Investigation report response was not valid JSON",
                    exception
            );
        }
    }

    private String mergeExecutiveSummary(
            Map<String, Object> parsed,
            String fallback
    ) {
        String llmSummary = optionalString(parsed.get("executiveSummary"));
        if (llmSummary.isBlank()) {
            return fallback;
        }
        return deterministicGenerator.truncateWords(llmSummary, 200);
    }

    private InvestigationReportSection mergeSection(
            Object rawSection,
            InvestigationReportSection deterministicSection
    ) {
        if (!(rawSection instanceof Map<?, ?> sectionMap)) {
            return deterministicSection;
        }

        String narrative = optionalString(sectionMap.get("narrative"));
        if (narrative.isBlank()) {
            return deterministicSection;
        }

        String title = sectionMap.get("title") == null
                ? deterministicSection.title()
                : String.valueOf(sectionMap.get("title"));

        return new InvestigationReportSection(
                title,
                narrative,
                deterministicSection.deterministicFacts()
        );
    }

    private List<InvestigationReportSection> mergeSupportingEvidence(
            Map<String, Object> parsed,
            List<InvestigationReportSection> deterministicEvidence
    ) {
        if (deterministicEvidence.size() == 1
                && "No supporting documentation was retrieved."
                        .equals(deterministicEvidence.getFirst().narrative())) {
            return deterministicEvidence;
        }

        Object rawEvidence = parsed.get("supportingEvidence");
        if (!(rawEvidence instanceof List<?> llmItems) || llmItems.isEmpty()) {
            return deterministicEvidence;
        }

        List<InvestigationReportSection> merged = new ArrayList<>();
        int limit = Math.min(llmItems.size(), deterministicEvidence.size());
        for (int index = 0; index < limit; index++) {
            Object llmItem = llmItems.get(index);
            InvestigationReportSection deterministicItem =
                    deterministicEvidence.get(index);
            merged.add(mergeSection(llmItem, deterministicItem));
        }

        for (int index = limit; index < deterministicEvidence.size(); index++) {
            merged.add(deterministicEvidence.get(index));
        }

        return List.copyOf(merged);
    }

    private String mergeText(Object llmValue, String fallback) {
        String llmText = optionalString(llmValue);
        return llmText.isBlank() ? fallback : llmText;
    }

    private String optionalString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
