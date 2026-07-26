package com.umeshowl.banking.investigation.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InvestigationEvidenceQueryBuilder {

    private static final Map<String, List<String>> BASE_TERMS = Map.of(
            "FRAUD", List.of(
                    "fraud detection policy",
                    "transaction monitoring rules",
                    "suspicious transaction indicators",
                    "account takeover behavioral fraud guidance"
            ),
            "KYC", List.of(
                    "customer identification requirements",
                    "KYC verification standards",
                    "PEP due diligence",
                    "source of funds requirements",
                    "high-risk customer onboarding",
                    "periodic review requirements"
            ),
            "AML", List.of(
                    "structuring smurfing rules",
                    "transaction reporting thresholds",
                    "high-risk jurisdictions",
                    "rapid movement of funds",
                    "suspicious activity escalation",
                    "PEP transaction monitoring"
            ),
            "COMPLIANCE", List.of(
                    "investigation disposition policy",
                    "analyst review requirements",
                    "escalation criteria",
                    "approval rejection procedures",
                    "documentation audit requirements"
            )
    );

    private final ObjectMapper objectMapper;

    public InvestigationEvidenceQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildQuery(
            String agentType,
            InvestigationCase investigationCase,
            AgentFinding finding
    ) {
        List<String> terms = new ArrayList<>(BASE_TERMS.getOrDefault(
                agentType,
                List.of("banking investigation policy")
        ));

        appendCaseContext(terms, investigationCase);
        appendStructuredContext(terms, agentType, finding);

        return terms.stream()
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));
    }

    public String matchedReasonFor(
            String agentType,
            InvestigationCase investigationCase,
            AgentFinding finding
    ) {
        List<String> indicators = extractIndicatorTypes(agentType, finding);
        if (!indicators.isEmpty()) {
            return "Matched "
                    + agentType
                    + " policy guidance for triggered indicators: "
                    + String.join(", ", indicators);
        }

        return "Matched "
                + agentType
                + " policy guidance for investigation "
                + investigationCase.getTitle();
    }

    private void appendCaseContext(
            List<String> terms,
            InvestigationCase investigationCase
    ) {
        terms.add(investigationCase.getCaseType() + " investigation");
        terms.add(investigationCase.getTitle());
        terms.add(investigationCase.getDescription());

        MockCustomer customer = investigationCase.getCustomer();
        if (customer == null && investigationCase.getTransaction() != null) {
            customer = investigationCase.getTransaction().getCustomer();
        }

        if (customer != null) {
            terms.add("customer risk rating " + safe(customer.getRiskRating()));
            terms.add("KYC status " + safe(customer.getKycStatus()));
            terms.add("PEP status " + safe(customer.getPepStatus()));
            terms.add("account status " + safe(customer.getAccountStatus()));
        }

        MockTransaction transaction = investigationCase.getTransaction();
        if (transaction != null) {
            terms.add("transaction reference " + safe(
                    transaction.getTransactionReference()
            ));
            terms.add("transaction channel " + safe(transaction.getChannel()));
            terms.add("origin country " + safe(transaction.getOriginCountry()));
            terms.add("destination country "
                    + safe(transaction.getDestinationCountry()));
            if (transaction.isFlagged()) {
                terms.add("flagged transaction review");
            }
        }
    }

    private void appendStructuredContext(
            List<String> terms,
            String agentType,
            AgentFinding finding
    ) {
        if (finding == null || finding.getStructuredJson() == null) {
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    finding.getStructuredJson(),
                    new TypeReference<>() {
                    }
            );

            Object recommendation = payload.get("recommendation");
            if (recommendation != null) {
                terms.add("recommendation " + recommendation);
            }

            Object riskLevel = payload.get("riskLevel");
            if (riskLevel != null) {
                terms.add("risk level " + riskLevel);
            }

            terms.addAll(mapIndicatorsToTerms(
                    agentType,
                    payload.get("triggeredIndicators")
            ));
            terms.addAll(mapIndicatorsToTerms(
                    agentType,
                    payload.get("contributingFindings")
            ));
        } catch (Exception ignored) {
            terms.add(finding.getSummary());
        }
    }

    private List<String> extractIndicatorTypes(
            String agentType,
            AgentFinding finding
    ) {
        if (finding == null || finding.getStructuredJson() == null) {
            return List.of();
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    finding.getStructuredJson(),
                    new TypeReference<>() {
                    }
            );
            Set<String> indicators = new LinkedHashSet<>();
            indicators.addAll(readIndicatorTypes(payload.get("triggeredIndicators")));
            indicators.addAll(readIndicatorTypes(payload.get("contributingFindings")));
            return List.copyOf(indicators);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> readIndicatorTypes(Object rawIndicators) {
        if (!(rawIndicators instanceof List<?> indicators)) {
            return List.of();
        }

        List<String> types = new ArrayList<>();
        for (Object indicator : indicators) {
            if (indicator instanceof Map<?, ?> map && map.get("type") != null) {
                types.add(String.valueOf(map.get("type")));
            }
        }
        return types;
    }

    private List<String> mapIndicatorsToTerms(
            String agentType,
            Object rawIndicators
    ) {
        return readIndicatorTypes(rawIndicators).stream()
                .map(type -> indicatorTerm(agentType, type))
                .toList();
    }

    private String indicatorTerm(String agentType, String indicatorType) {
        return switch (agentType + ":" + indicatorType) {
            case "FRAUD:STRUCTURING" -> "structuring and threshold avoidance";
            case "FRAUD:RAPID_MOVEMENT" -> "rapid movement of funds";
            case "FRAUD:HIGH_RISK_COUNTRY" -> "high-risk country transfers";
            case "FRAUD:FLAGGED_TRANSACTION" -> "flagged transaction escalation";
            case "FRAUD:UNUSUAL_CHANNEL" -> "unusual payment channel monitoring";
            case "FRAUD:PROFILE_MISMATCH" -> "customer profile mismatch review";
            case "KYC:PEP_CUSTOMER" -> "politically exposed person due diligence";
            case "KYC:KYC_NOT_VERIFIED" -> "customer identification verification";
            case "KYC:MISSING_SOURCE_OF_FUNDS" -> "source of funds documentation";
            case "KYC:HIGH_RISK_NATIONALITY", "KYC:HIGH_RISK_RESIDENCE" ->
                    "high-risk geography onboarding";
            case "AML:STRUCTURING" -> "structuring smurfing reporting threshold";
            case "AML:PEP_ACTIVITY" -> "PEP transaction monitoring";
            case "AML:LARGE_TRANSACTION" -> "large transaction reporting threshold";
            case "AML:NEW_ACCOUNT_ACTIVITY" -> "new account activity monitoring";
            case "COMPLIANCE:MULTIPLE_ESCALATIONS" -> "escalation criteria";
            case "COMPLIANCE:MANUAL_REVIEW_REQUIRED" ->
                    "manual compliance review requirements";
            default -> indicatorType.toLowerCase().replace('_', ' ');
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
