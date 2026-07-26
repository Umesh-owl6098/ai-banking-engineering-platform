package com.umeshowl.banking.investigation.explainability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExplainabilityService {

    private static final List<String> EXPLAINABILITY_AGENT_TYPES = List.of(
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE"
    );

    private final InvestigationCaseService investigationCaseService;
    private final AgentFindingRepository agentFindingRepository;
    private final AgentFindingCitationRepository citationRepository;
    private final ExplainabilityRuleMetadataResolver metadataResolver;
    private final ObjectMapper objectMapper;

    public ExplainabilityService(
            InvestigationCaseService investigationCaseService,
            AgentFindingRepository agentFindingRepository,
            AgentFindingCitationRepository citationRepository,
            ExplainabilityRuleMetadataResolver metadataResolver,
            ObjectMapper objectMapper
    ) {
        this.investigationCaseService = investigationCaseService;
        this.agentFindingRepository = agentFindingRepository;
        this.citationRepository = citationRepository;
        this.metadataResolver = metadataResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ExplainabilityResponse> explainInvestigation(
            UUID investigationId
    ) {
        investigationCaseService.getCase(investigationId);

        return EXPLAINABILITY_AGENT_TYPES.stream()
                .map(agentType -> latestCompleteFinding(
                        investigationId,
                        agentType
                ))
                .flatMap(java.util.Optional::stream)
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExplainabilityResponse explainFinding(UUID findingId) {
        AgentFinding finding = agentFindingRepository.findById(findingId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Agent finding not found: " + findingId
                        )
                );

        return buildResponse(finding);
    }

    private ExplainabilityResponse buildResponse(AgentFinding finding) {
        InvestigationCase investigationCase =
                finding.getInvestigationCase();
        Map<String, Object> structured = parseStructuredJson(
                finding.getStructuredJson()
        );
        List<AgentFindingCitation> citations =
                citationRepository.findByFinding_IdOrderByCreatedAtAsc(
                        finding.getId()
                );
        List<ExplainabilityEvidence> evidenceItems =
                mapCitations(citations, finding.getAgentType());

        String agentType = finding.getAgentType();
        int totalScore = extractTotalScore(agentType, structured);
        String recommendation = stringValue(structured.get("recommendation"));
        List<Map<String, Object>> rawIndicators = extractIndicators(structured);

        Map<String, Object> customerFields = buildCustomerFields(
                investigationCase
        );
        Map<String, Object> transactionFields = buildTransactionFields(
                investigationCase
        );

        int indicatorScoreTotal = rawIndicators.stream()
                .mapToInt(this::scoreContribution)
                .sum();
        int scoreBasis = indicatorScoreTotal > 0
                ? indicatorScoreTotal
                : Math.max(totalScore, 1);
        BigDecimal findingConfidence = finding.getConfidence() == null
                ? BigDecimal.ZERO
                : finding.getConfidence();

        List<ExplainabilityRule> rules = rawIndicators.stream()
                .map(indicator -> toRule(
                        agentType,
                        indicator,
                        customerFields,
                        transactionFields,
                        evidenceItems,
                        findingConfidence,
                        scoreBasis
                ))
                .toList();

        return new ExplainabilityResponse(
                finding.getId(),
                investigationCase.getId(),
                agentType,
                totalScore,
                finding.getRiskLevel(),
                recommendation,
                findingConfidence,
                finding.getSummary(),
                rules,
                customerFields,
                transactionFields,
                evidenceItems
        );
    }

    private ExplainabilityRule toRule(
            String agentType,
            Map<String, Object> indicator,
            Map<String, Object> customerFields,
            Map<String, Object> transactionFields,
            List<ExplainabilityEvidence> evidenceItems,
            BigDecimal findingConfidence,
            int scoreBasis
    ) {
        String ruleCode = stringValue(indicator.get("type"));
        int scoreContribution = scoreContribution(indicator);
        Map<String, Object> evidenceValues = extractMap(indicator.get("evidenceValues"));
        Map<String, Object> configuredThresholds =
                metadataResolver.thresholds(agentType, ruleCode);
        Map<String, Object> mergedThresholds = new LinkedHashMap<>(
                configuredThresholds
        );
        evidenceValues.forEach((key, value) -> {
            if (key.toLowerCase(Locale.ROOT).contains("threshold")) {
                mergedThresholds.putIfAbsent(key, value);
            }
        });

        return new ExplainabilityRule(
                ruleCode,
                metadataResolver.displayName(ruleCode),
                scoreContribution,
                stringValue(indicator.get("explanation")),
                metadataResolver.description(agentType, ruleCode),
                evidenceValues,
                mergedThresholds,
                relatedFieldsForRule(
                        ruleCode,
                        evidenceValues,
                        customerFields,
                        transactionFields
                ),
                confidenceContribution(
                        findingConfidence,
                        scoreContribution,
                        scoreBasis
                ),
                evidenceItems
        );
    }

    private Map<String, Object> relatedFieldsForRule(
            String ruleCode,
            Map<String, Object> evidenceValues,
            Map<String, Object> customerFields,
            Map<String, Object> transactionFields
    ) {
        Map<String, Object> related = new LinkedHashMap<>();

        if (isTransactionRule(ruleCode)) {
            related.putAll(transactionFields);
        }
        if (isCustomerRule(ruleCode)) {
            related.putAll(customerFields);
        }
        if (!evidenceValues.isEmpty()) {
            related.put("triggeringValues", evidenceValues);
        }

        return Map.copyOf(related);
    }

    private boolean isTransactionRule(String ruleCode) {
        return ruleCode != null && (
                ruleCode.contains("TRANSACTION")
                        || ruleCode.contains("MOVEMENT")
                        || ruleCode.contains("STRUCTURING")
                        || ruleCode.contains("CHANNEL")
                        || ruleCode.contains("COUNTRY")
        );
    }

    private boolean isCustomerRule(String ruleCode) {
        return ruleCode != null && (
                ruleCode.contains("CUSTOMER")
                        || ruleCode.contains("KYC")
                        || ruleCode.contains("PEP")
                        || ruleCode.contains("ACCOUNT")
                        || ruleCode.contains("OCCUPATION")
                        || ruleCode.contains("PROFILE")
                        || ruleCode.contains("NATIONALITY")
                        || ruleCode.contains("RESIDENCE")
        );
    }

    private BigDecimal confidenceContribution(
            BigDecimal findingConfidence,
            int scoreContribution,
            int scoreBasis
    ) {
        if (findingConfidence == null
                || scoreContribution <= 0
                || scoreBasis <= 0) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }

        return findingConfidence
                .multiply(BigDecimal.valueOf(scoreContribution))
                .divide(
                        BigDecimal.valueOf(scoreBasis),
                        3,
                        RoundingMode.HALF_UP
                );
    }

    private List<ExplainabilityEvidence> mapCitations(
            List<AgentFindingCitation> citations,
            String agentType
    ) {
        return citations.stream()
                .map(citation -> new ExplainabilityEvidence(
                        citation.getId(),
                        citation.getFileName(),
                        citation.getChunkIndex(),
                        citation.getSimilarity(),
                        citation.getContentPreview(),
                        "Retrieved policy evidence supporting "
                                + agentType
                                + " analysis."
                ))
                .toList();
    }

    private Map<String, Object> buildCustomerFields(
            InvestigationCase investigationCase
    ) {
        MockCustomer customer = investigationCase.getCustomer();
        if (customer == null && investigationCase.getTransaction() != null) {
            customer = investigationCase.getTransaction().getCustomer();
        }
        if (customer == null) {
            return Map.of();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        putIfNotNull(fields, "fullName", customer.getFullName());
        putIfNotNull(fields, "accountNumber", customer.getAccountNumber());
        putIfNotNull(fields, "riskRating", customer.getRiskRating());
        putIfNotNull(fields, "kycStatus", customer.getKycStatus());
        putIfNotNull(fields, "pepStatus", customer.getPepStatus());
        putIfNotNull(fields, "occupation", customer.getOccupation());
        putIfNotNull(fields, "sourceOfFunds", customer.getSourceOfFunds());
        putIfNotNull(fields, "countryOfResidence", customer.getCountryOfResidence());
        putIfNotNull(fields, "nationality", customer.getNationality());
        putIfNotNull(fields, "accountStatus", customer.getAccountStatus());
        return Map.copyOf(fields);
    }

    private Map<String, Object> buildTransactionFields(
            InvestigationCase investigationCase
    ) {
        MockTransaction transaction = investigationCase.getTransaction();
        if (transaction == null) {
            return Map.of();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        putIfNotNull(fields, "transactionReference", transaction.getTransactionReference());
        putIfNotNull(fields, "amount", transaction.getAmount());
        putIfNotNull(fields, "currency", transaction.getCurrency());
        putIfNotNull(fields, "channel", transaction.getChannel());
        fields.put("flagged", transaction.isFlagged());
        putIfNotNull(fields, "riskScore", transaction.getRiskScore());
        putIfNotNull(fields, "originCountry", transaction.getOriginCountry());
        putIfNotNull(fields, "destinationCountry", transaction.getDestinationCountry());
        putIfNotNull(fields, "counterpartyName", transaction.getCounterpartyName());
        putIfNotNull(fields, "counterpartyCountry", transaction.getCounterpartyCountry());
        return Map.copyOf(fields);
    }

    private java.util.Optional<AgentFinding> latestCompleteFinding(
            UUID investigationId,
            String agentType
    ) {
        return agentFindingRepository
                .findByInvestigationCase_IdAndAgentType(
                        investigationId,
                        agentType
                )
                .stream()
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .max(Comparator.comparing(AgentFinding::getCreatedAt));
    }

    private Map<String, Object> parseStructuredJson(String structuredJson) {
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
    private List<Map<String, Object>> extractIndicators(
            Map<String, Object> structured
    ) {
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

    private int extractTotalScore(
            String agentType,
            Map<String, Object> structured
    ) {
        Object score = switch (agentType) {
            case "FRAUD" -> structured.get("fraudScore");
            case "KYC" -> structured.get("kycScore");
            case "AML" -> structured.get("amlScore");
            case "COMPLIANCE" -> structured.get("overallScore");
            default -> null;
        };

        if (score instanceof Number number) {
            return number.intValue();
        }

        return 0;
    }

    private int scoreContribution(Map<String, Object> indicator) {
        Object value = indicator.get("scoreContribution");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void putIfNotNull(
            Map<String, Object> target,
            String key,
            Object value
    ) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
