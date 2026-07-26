package com.umeshowl.banking.investigation.report;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DeterministicInvestigationReportGenerator {

    private static final int EXECUTIVE_SUMMARY_MAX_WORDS = 200;

    public InvestigationReport generate(
            UUID investigationId,
            ReportSourceData source,
            InvestigationReportMetadata metadata
    ) {
        return new InvestigationReport(
                null,
                investigationId,
                metadata,
                buildExecutiveSummary(source),
                buildInvestigationOverview(source),
                buildCustomerRiskProfile(source),
                buildFraudAnalysis(source),
                buildKycAnalysis(source),
                buildAmlAnalysis(source),
                buildComplianceAssessment(source),
                buildSupportingEvidence(source),
                resolveAnalystRecommendation(source),
                buildConfidenceExplanation(source),
                buildLimitations()
        );
    }

    String buildExecutiveSummary(ReportSourceData source) {
        StringBuilder summary = new StringBuilder();
        appendInvestigationSummary(summary, source);
        appendCustomerSummary(summary, source);
        appendTransactionSummary(summary, source);
        appendRiskSummary(summary, source);
        return truncateWords(summary.toString().trim(), EXECUTIVE_SUMMARY_MAX_WORDS);
    }

    InvestigationReportSection buildInvestigationOverview(ReportSourceData source) {
        Map<String, Object> investigation = source.investigation();
        String narrative = String.format(
                "Investigation \"%s\" (%s) is currently %s with %s priority. %s",
                stringValue(investigation.get("title"), "Untitled"),
                stringValue(investigation.get("caseType"), "UNKNOWN"),
                stringValue(investigation.get("status"), "OPEN"),
                stringValue(investigation.get("priority"), "MEDIUM"),
                stringValue(investigation.get("description"), "")
        ).trim();

        return InvestigationReportSection.of(
                "Investigation Overview",
                narrative
        ).withDeterministicFacts(investigation);
    }

    InvestigationReportSection buildCustomerRiskProfile(ReportSourceData source) {
        Map<String, Object> customer = source.customer();
        if (!isAvailable(customer)) {
            return InvestigationReportSection.of(
                    "Customer Risk Profile",
                    "No customer profile was linked to this investigation."
            );
        }

        String narrative = String.format(
                "Customer %s (account %s) has a %s risk rating and KYC status %s. "
                        + "PEP status is %s. Occupation: %s. Source of funds: %s. "
                        + "Country of residence: %s.",
                stringValue(customer.get("name"), "Unknown"),
                stringValue(customer.get("accountNumber"), "N/A"),
                stringValue(customer.get("riskRating"), "UNKNOWN"),
                stringValue(customer.get("kycStatus"), "UNKNOWN"),
                stringValue(customer.get("pepStatus"), "NONE"),
                stringValue(customer.get("occupation"), "Not recorded"),
                stringValue(customer.get("sourceOfFunds"), "Not recorded"),
                stringValue(customer.get("countryOfResidence"), "Unknown")
        );

        Map<String, Object> facts = new LinkedHashMap<>(customer);
        Map<String, Object> kycFinding = source.kycFinding();
        if (isAvailable(kycFinding)) {
            facts.put("kycScore", kycFinding.get("score"));
            facts.put("kycRiskLevel", kycFinding.get("riskLevel"));
            facts.put("kycRecommendation", kycFinding.get("recommendation"));
        }

        return InvestigationReportSection.of(
                "Customer Risk Profile",
                narrative
        ).withDeterministicFacts(facts);
    }

    InvestigationReportSection buildFraudAnalysis(ReportSourceData source) {
        Map<String, Object> fraudFinding = source.fraudFinding();
        if (!isAvailable(fraudFinding)) {
            return InvestigationReportSection.of(
                    "Fraud Analysis",
                    "No completed fraud finding is available for this investigation."
            );
        }

        List<Map<String, Object>> indicators = indicators(fraudFinding);
        String indicatorText = describeIndicators(indicators);
        String transactionText = describeAffectedTransaction(source);

        String narrative = String.format(
                "Fraud score %s with %s risk level. Recommendation: %s. "
                        + "Triggered rules: %s. %s %s",
                stringValue(fraudFinding.get("score"), "N/A"),
                stringValue(fraudFinding.get("riskLevel"), "UNKNOWN"),
                stringValue(fraudFinding.get("recommendation"), "REVIEW"),
                indicatorText,
                transactionText,
                stringValue(fraudFinding.get("summary"), "")
        ).trim();

        return InvestigationReportSection.of(
                "Fraud Analysis",
                narrative
        ).withDeterministicFacts(fraudFinding);
    }

    InvestigationReportSection buildKycAnalysis(ReportSourceData source) {
        Map<String, Object> kycFinding = source.kycFinding();
        Map<String, Object> customer = source.customer();

        if (!isAvailable(kycFinding)) {
            return InvestigationReportSection.of(
                    "KYC Analysis",
                    "No completed KYC finding is available for this investigation."
            );
        }

        String narrative = String.format(
                "KYC score %s with %s risk level. Recommendation: %s. "
                        + "KYC completeness and profile checks: %s. "
                        + "Customer KYC status: %s, PEP: %s, occupation: %s, "
                        + "source of funds: %s. %s",
                stringValue(kycFinding.get("score"), "N/A"),
                stringValue(kycFinding.get("riskLevel"), "UNKNOWN"),
                stringValue(kycFinding.get("recommendation"), "REVIEW"),
                describeIndicators(indicators(kycFinding)),
                isAvailable(customer)
                        ? stringValue(customer.get("kycStatus"), "UNKNOWN")
                        : "Unknown",
                isAvailable(customer)
                        ? stringValue(customer.get("pepStatus"), "NONE")
                        : "Unknown",
                isAvailable(customer)
                        ? stringValue(customer.get("occupation"), "Not recorded")
                        : "Not recorded",
                isAvailable(customer)
                        ? stringValue(customer.get("sourceOfFunds"), "Not recorded")
                        : "Not recorded",
                stringValue(kycFinding.get("summary"), "")
        ).trim();

        return InvestigationReportSection.of(
                "KYC Analysis",
                narrative
        ).withDeterministicFacts(kycFinding);
    }

    InvestigationReportSection buildAmlAnalysis(ReportSourceData source) {
        Map<String, Object> amlFinding = source.amlFinding();
        if (!isAvailable(amlFinding)) {
            return InvestigationReportSection.of(
                    "AML Analysis",
                    "No completed AML finding is available for this investigation."
            );
        }

        String narrative = String.format(
                "AML score %s with %s risk level. Recommendation: %s. "
                        + "Suspicious activity indicators include: %s. "
                        + "Review areas: structuring, rapid movement, high-risk countries, "
                        + "and other triggered AML patterns. %s",
                stringValue(amlFinding.get("score"), "N/A"),
                stringValue(amlFinding.get("riskLevel"), "UNKNOWN"),
                stringValue(amlFinding.get("recommendation"), "REVIEW"),
                describeIndicators(indicators(amlFinding)),
                stringValue(amlFinding.get("summary"), "")
        ).trim();

        return InvestigationReportSection.of(
                "AML Analysis",
                narrative
        ).withDeterministicFacts(amlFinding);
    }

    InvestigationReportSection buildComplianceAssessment(ReportSourceData source) {
        Map<String, Object> complianceFinding = source.complianceFinding();
        if (!isAvailable(complianceFinding)) {
            return InvestigationReportSection.of(
                    "Compliance Assessment",
                    "No completed compliance finding is available for this investigation."
            );
        }

        String narrative = String.format(
                "Overall compliance score %s with %s risk level. "
                        + "Deterministic recommendation: %s. "
                        + "This recommendation reflects consolidated fraud, KYC, and AML findings. %s",
                stringValue(complianceFinding.get("score"), "N/A"),
                stringValue(complianceFinding.get("riskLevel"), "UNKNOWN"),
                stringValue(complianceFinding.get("recommendation"), "REVIEW"),
                stringValue(complianceFinding.get("summary"), "")
        ).trim();

        return InvestigationReportSection.of(
                "Compliance Assessment",
                narrative
        ).withDeterministicFacts(complianceFinding);
    }

    List<InvestigationReportSection> buildSupportingEvidence(ReportSourceData source) {
        if (source.evidence().isEmpty()) {
            return List.of(InvestigationReportSection.of(
                    "Supporting Evidence",
                    "No supporting documentation was retrieved."
            ));
        }

        List<InvestigationReportSection> sections = new ArrayList<>();
        for (Map<String, Object> item : source.evidence()) {
            String narrative = String.format(
                    "Document: %s. Excerpt: %s. Relevance: Retrieved policy evidence "
                            + "for %s analysis (similarity %s).",
                    stringValue(item.get("documentName"), "Unknown document"),
                    stringValue(item.get("excerpt"), "No excerpt available"),
                    stringValue(item.get("agentType"), "agent"),
                    stringValue(item.get("similarity"), "N/A")
            );
            sections.add(InvestigationReportSection.of(
                    stringValue(item.get("documentName"), "Supporting Evidence"),
                    narrative
            ).withDeterministicFacts(item));
        }
        return List.copyOf(sections);
    }

    String resolveAnalystRecommendation(ReportSourceData source) {
        Map<String, Object> complianceFinding = source.complianceFinding();
        if (isAvailable(complianceFinding)
                && complianceFinding.get("recommendation") != null) {
            return String.valueOf(complianceFinding.get("recommendation"));
        }
        return "REVIEW";
    }

    String buildConfidenceExplanation(ReportSourceData source) {
        int indicatorCount = countIndicators(source);
        int evidenceCount = source.evidence().size();
        Map<String, Object> complianceFinding = source.complianceFinding();

        return String.format(
                "Confidence is driven by deterministic scoring. "
                        + "Overall compliance score: %s. "
                        + "Triggered indicators across agents: %d. "
                        + "Retrieved evidence items: %d. "
                        + "Compliance confidence: %s.",
                isAvailable(complianceFinding)
                        ? stringValue(complianceFinding.get("score"), "N/A")
                        : "N/A",
                indicatorCount,
                evidenceCount,
                isAvailable(complianceFinding)
                        ? stringValue(complianceFinding.get("confidence"), "N/A")
                        : "N/A"
        );
    }

    String buildLimitations() {
        return "Deterministic agent rules were the primary source of truth for scores, "
                + "risk levels, and recommendations. "
                + "This report summary is advisory and must not override deterministic outputs. "
                + "Human review is required before any final decision.";
    }

    private void appendInvestigationSummary(
            StringBuilder summary,
            ReportSourceData source
    ) {
        Map<String, Object> investigation = source.investigation();
        summary.append("Investigation \"")
                .append(stringValue(investigation.get("title"), "Untitled"))
                .append("\" (")
                .append(stringValue(investigation.get("caseType"), "UNKNOWN"))
                .append(") with ")
                .append(stringValue(investigation.get("priority"), "MEDIUM"))
                .append(" priority. ");
    }

    private void appendCustomerSummary(
            StringBuilder summary,
            ReportSourceData source
    ) {
        Map<String, Object> customer = source.customer();
        if (!isAvailable(customer)) {
            return;
        }
        summary.append("Customer ")
                .append(stringValue(customer.get("name"), "Unknown"))
                .append(" has ")
                .append(stringValue(customer.get("riskRating"), "UNKNOWN"))
                .append(" risk rating and KYC status ")
                .append(stringValue(customer.get("kycStatus"), "UNKNOWN"))
                .append(". ");
    }

    private void appendTransactionSummary(
            StringBuilder summary,
            ReportSourceData source
    ) {
        Map<String, Object> transaction = source.transaction();
        if (!isAvailable(transaction)) {
            return;
        }
        summary.append("Transaction ")
                .append(stringValue(transaction.get("reference"), "N/A"))
                .append(" for ")
                .append(stringValue(transaction.get("amount"), "unknown"))
                .append(" ")
                .append(stringValue(transaction.get("currency"), ""))
                .append(" via ")
                .append(stringValue(transaction.get("channel"), "unknown channel"))
                .append(". ");
    }

    private void appendRiskSummary(
            StringBuilder summary,
            ReportSourceData source
    ) {
        Map<String, Object> complianceFinding = source.complianceFinding();
        if (isAvailable(complianceFinding)) {
            summary.append("Overall risk level ")
                    .append(stringValue(complianceFinding.get("riskLevel"), "UNKNOWN"))
                    .append(" with recommendation ")
                    .append(stringValue(complianceFinding.get("recommendation"), "REVIEW"))
                    .append(". ");
        }

        Map<String, Object> fraudFinding = source.fraudFinding();
        if (isAvailable(fraudFinding)) {
            summary.append("Fraud score ")
                    .append(stringValue(fraudFinding.get("score"), "N/A"))
                    .append(". ");
        }
    }

    private String describeAffectedTransaction(ReportSourceData source) {
        Map<String, Object> transaction = source.transaction();
        if (!isAvailable(transaction)) {
            return "No linked transaction was available for fraud context.";
        }
        return String.format(
                "Affected transaction %s (%s %s, flagged=%s, risk score=%s).",
                stringValue(transaction.get("reference"), "N/A"),
                stringValue(transaction.get("amount"), "unknown"),
                stringValue(transaction.get("currency"), ""),
                stringValue(transaction.get("flagged"), "false"),
                stringValue(transaction.get("riskScore"), "N/A")
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> indicators(Map<String, Object> finding) {
        Object rawIndicators = finding.get("indicators");
        if (rawIndicators instanceof List<?> items) {
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private String describeIndicators(List<Map<String, Object>> indicators) {
        if (indicators.isEmpty()) {
            return "none";
        }

        return indicators.stream()
                .map(indicator -> {
                    Object type = indicator.get("type");
                    Object explanation = indicator.get("explanation");
                    if (type != null && explanation != null) {
                        return type + " (" + explanation + ")";
                    }
                    if (type != null) {
                        return String.valueOf(type);
                    }
                    if (explanation != null) {
                        return String.valueOf(explanation);
                    }
                    return "indicator";
                })
                .collect(Collectors.joining("; "));
    }

    private int countIndicators(ReportSourceData source) {
        return indicators(source.fraudFinding()).size()
                + indicators(source.kycFinding()).size()
                + indicators(source.amlFinding()).size();
    }

    private boolean isAvailable(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        Object available = map.get("available");
        return available == null || Boolean.TRUE.equals(available);
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    String truncateWords(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) {
            return text.trim();
        }
        StringBuilder truncated = new StringBuilder();
        for (int index = 0; index < maxWords; index++) {
            if (index > 0) {
                truncated.append(' ');
            }
            truncated.append(words[index]);
        }
        return truncated.toString();
    }
}
