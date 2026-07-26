package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportContextAssembler {

    private final ObjectMapper objectMapper;

    public ReportContextAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReportSourceData assemble(
            InvestigationCase investigationCase,
            Map<String, AgentFinding> findingsByAgent,
            Map<String, List<AgentFindingCitation>> citationsByAgent
    ) {
        MockCustomer customer = investigationCase.getCustomer();
        if (customer == null && investigationCase.getTransaction() != null) {
            customer = investigationCase.getTransaction().getCustomer();
        }

        return new ReportSourceData(
                buildInvestigation(investigationCase),
                buildCustomer(customer),
                buildTransaction(investigationCase.getTransaction()),
                buildFinding(findingsByAgent.get("FRAUD")),
                buildFinding(findingsByAgent.get("KYC")),
                buildFinding(findingsByAgent.get("AML")),
                buildFinding(findingsByAgent.get("COMPLIANCE")),
                buildEvidence(citationsByAgent)
        );
    }

    private Map<String, Object> buildInvestigation(
            InvestigationCase investigationCase
    ) {
        Map<String, Object> investigation = new LinkedHashMap<>();
        investigation.put("id", investigationCase.getId());
        investigation.put("title", investigationCase.getTitle());
        investigation.put("description", investigationCase.getDescription());
        investigation.put("caseType", investigationCase.getCaseType());
        investigation.put("status", investigationCase.getStatus());
        investigation.put("priority", investigationCase.getPriority());
        investigation.put("createdAt", investigationCase.getCreatedAt());
        investigation.put("updatedAt", investigationCase.getUpdatedAt());
        return investigation;
    }

    private Map<String, Object> buildCustomer(MockCustomer customer) {
        if (customer == null) {
            return Map.of("available", false);
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("available", true);
        profile.put("name", customer.getFullName());
        profile.put("accountNumber", customer.getAccountNumber());
        profile.put("riskRating", customer.getRiskRating());
        profile.put("kycStatus", customer.getKycStatus());
        profile.put("pepStatus", customer.getPepStatus());
        profile.put("occupation", customer.getOccupation());
        profile.put("sourceOfFunds", customer.getSourceOfFunds());
        profile.put("accountStatus", customer.getAccountStatus());
        profile.put("countryOfResidence", customer.getCountryOfResidence());
        return profile;
    }

    private Map<String, Object> buildTransaction(MockTransaction transaction) {
        if (transaction == null) {
            return Map.of("available", false);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("available", true);
        details.put("reference", transaction.getTransactionReference());
        details.put("amount", transaction.getAmount());
        details.put("currency", transaction.getCurrency());
        details.put("channel", transaction.getChannel());
        details.put("originCountry", transaction.getOriginCountry());
        details.put("destinationCountry", transaction.getDestinationCountry());
        details.put("flagged", transaction.isFlagged());
        details.put("riskScore", transaction.getRiskScore());
        return details;
    }

    private Map<String, Object> buildFinding(AgentFinding finding) {
        if (finding == null) {
            return Map.of("available", false);
        }

        Map<String, Object> structured = parseStructuredJson(
                finding.getStructuredJson()
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", true);
        payload.put("agentType", finding.getAgentType());
        payload.put("summary", finding.getSummary());
        payload.put("riskLevel", finding.getRiskLevel());
        payload.put("confidence", finding.getConfidence());
        payload.put("score", extractScore(finding.getAgentType(), structured));
        payload.put("recommendation", structured.get("recommendation"));
        payload.put("indicators", extractIndicators(structured));
        payload.put("structured", structured);
        return payload;
    }

    private List<Map<String, Object>> buildEvidence(
            Map<String, List<AgentFindingCitation>> citationsByAgent
    ) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        citationsByAgent.forEach((agentType, citations) -> {
            for (AgentFindingCitation citation : citations) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("agentType", agentType);
                item.put("documentName", citation.getFileName());
                item.put("chunkIndex", citation.getChunkIndex());
                item.put("similarity", citation.getSimilarity());
                item.put("excerpt", citation.getContentPreview());
                evidence.add(item);
            }
        });
        return evidence;
    }

    Map<String, Object> parseStructuredJson(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    structuredJson,
                    new TypeReference<>() {
                    }
            );
        } catch (Exception exception) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> extractIndicators(Map<String, Object> structured) {
        Object indicators = structured.get("triggeredIndicators");
        if (indicators == null) {
            indicators = structured.get("contributingFindings");
        }
        if (!(indicators instanceof List<?> items)) {
            return List.of();
        }

        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                parsed.add((Map<String, Object>) map);
            }
        }
        return parsed;
    }

    Object extractScore(String agentType, Map<String, Object> structured) {
        return switch (agentType) {
            case "FRAUD" -> structured.get("fraudScore");
            case "KYC" -> structured.get("kycScore");
            case "AML" -> structured.get("amlScore");
            case "COMPLIANCE" -> structured.get("overallScore");
            default -> null;
        };
    }
}
