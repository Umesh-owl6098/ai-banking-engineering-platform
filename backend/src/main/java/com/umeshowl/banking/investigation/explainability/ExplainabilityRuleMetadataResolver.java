package com.umeshowl.banking.investigation.explainability;

import com.umeshowl.banking.investigation.aml.AmlAgentProperties;
import com.umeshowl.banking.investigation.compliance.ComplianceAgentProperties;
import com.umeshowl.banking.investigation.fraud.FraudAgentProperties;
import com.umeshowl.banking.investigation.kyc.KycAgentProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExplainabilityRuleMetadataResolver {

    private final FraudAgentProperties fraudProperties;
    private final KycAgentProperties kycProperties;
    private final AmlAgentProperties amlProperties;
    private final ComplianceAgentProperties complianceProperties;

    public ExplainabilityRuleMetadataResolver(
            FraudAgentProperties fraudProperties,
            KycAgentProperties kycProperties,
            AmlAgentProperties amlProperties,
            ComplianceAgentProperties complianceProperties
    ) {
        this.fraudProperties = fraudProperties;
        this.kycProperties = kycProperties;
        this.amlProperties = amlProperties;
        this.complianceProperties = complianceProperties;
    }

    public String displayName(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return "Unknown Rule";
        }

        return java.util.Arrays.stream(ruleCode.split("_"))
                .filter(part -> !part.isBlank())
                .map(part ->
                        part.substring(0, 1).toUpperCase(Locale.ROOT)
                                + part.substring(1).toLowerCase(Locale.ROOT)
                )
                .collect(Collectors.joining(" "));
    }

    public String description(String agentType, String ruleCode) {
        return switch (agentType + ":" + ruleCode) {
            case "FRAUD:FLAGGED_TRANSACTION" ->
                    "Flags transactions explicitly marked for enhanced review.";
            case "FRAUD:HIGH_TRANSACTION_RISK_SCORE" ->
                    "Evaluates the transaction risk score against the configured threshold.";
            case "FRAUD:LARGE_TRANSACTION" ->
                    "Detects transactions above the configured amount threshold.";
            case "FRAUD:HIGH_RISK_COUNTRY" ->
                    "Checks origin or destination countries against the high-risk list.";
            case "FRAUD:RAPID_MOVEMENT" ->
                    "Identifies rapid movement of funds across multiple transactions.";
            case "FRAUD:STRUCTURING" ->
                    "Detects structuring patterns below reporting thresholds.";
            case "FRAUD:UNUSUAL_CHANNEL" ->
                    "Compares transaction channel usage against customer history.";
            case "FRAUD:CUSTOMER_PROFILE_MISMATCH" ->
                    "Compares transaction behavior against customer profile expectations.";
            case "KYC:KYC_NOT_VERIFIED" ->
                    "Checks whether customer KYC verification is incomplete.";
            case "KYC:MISSING_OCCUPATION" ->
                    "Checks whether occupation information is missing.";
            case "KYC:MISSING_SOURCE_OF_FUNDS" ->
                    "Checks whether source of funds is documented.";
            case "KYC:PEP_CUSTOMER" ->
                    "Identifies politically exposed person status.";
            case "KYC:HIGH_CUSTOMER_RISK" ->
                    "Evaluates the customer risk rating.";
            case "KYC:HIGH_RISK_NATIONALITY" ->
                    "Checks customer nationality against restricted countries.";
            case "KYC:HIGH_RISK_RESIDENCE" ->
                    "Checks country of residence against restricted countries.";
            case "KYC:NEW_ACCOUNT" ->
                    "Flags recently opened accounts.";
            case "KYC:INACTIVE_OR_RESTRICTED_ACCOUNT" ->
                    "Checks account status restrictions.";
            case "KYC:PROFILE_INCONSISTENCY" ->
                    "Detects inconsistencies in customer profile data.";
            case "AML:STRUCTURING" ->
                    "Detects AML structuring patterns below reporting thresholds.";
            case "AML:RAPID_MOVEMENT" ->
                    "Detects rapid fund movement within the configured window.";
            case "AML:LARGE_TRANSACTION" ->
                    "Detects large-value transactions above AML thresholds.";
            case "AML:HIGH_RISK_COUNTRY" ->
                    "Checks transaction countries against AML high-risk lists.";
            case "AML:HIGH_RISK_CUSTOMER" ->
                    "Evaluates customer AML risk rating.";
            case "AML:PEP_ACTIVITY" ->
                    "Detects PEP-related activity indicators.";
            case "AML:FLAGGED_TRANSACTION" ->
                    "Flags explicitly marked suspicious transactions.";
            case "AML:HIGH_TRANSACTION_RISK_SCORE" ->
                    "Evaluates transaction risk score for AML concerns.";
            case "AML:NEW_ACCOUNT_ACTIVITY" ->
                    "Detects unusual activity on newly opened accounts.";
            case "AML:MULTIPLE_HIGH_RISK_INDICATORS" ->
                    "Detects multiple concurrent AML high-risk indicators.";
            case "COMPLIANCE:MULTIPLE_HIGH_RISK_FINDINGS" ->
                    "Consolidates when multiple specialist findings are high risk.";
            case "COMPLIANCE:FRAUD_CRITICAL" ->
                    "Escalates when fraud analysis is critical.";
            case "COMPLIANCE:AML_CRITICAL" ->
                    "Escalates when AML analysis is critical.";
            case "COMPLIANCE:KYC_CRITICAL" ->
                    "Escalates when KYC analysis is critical.";
            case "COMPLIANCE:FRAUD_AND_AML_COMBINATION" ->
                    "Escalates when fraud and AML findings align.";
            case "COMPLIANCE:PEP_WITH_AML" ->
                    "Escalates when PEP activity appears in AML findings.";
            case "COMPLIANCE:HIGH_CONFIDENCE_MATCH" ->
                    "Escalates when multiple high-risk findings have high confidence.";
            case "COMPLIANCE:MULTIPLE_ESCALATIONS" ->
                    "Escalates when multiple specialist agents recommend escalation.";
            case "COMPLIANCE:CONSISTENT_HIGH_RISK_PATTERN" ->
                    "Escalates when all specialist analyses show high risk.";
            case "COMPLIANCE:MANUAL_REVIEW_REQUIRED" ->
                    "Requires human compliance review when indicators are present.";
            default -> "Deterministic rule evaluation for " + ruleCode + ".";
        };
    }

    public Map<String, Object> thresholds(String agentType, String ruleCode) {
        Map<String, Object> thresholds = new LinkedHashMap<>();

        switch (agentType + ":" + ruleCode) {
            case "FRAUD:FLAGGED_TRANSACTION" ->
                    thresholds.put("scoreContribution", fraudProperties.getFlaggedTransactionScore());
            case "FRAUD:HIGH_TRANSACTION_RISK_SCORE" -> {
                thresholds.put("scoreContribution", fraudProperties.getHighTransactionRiskScore());
                thresholds.put("riskScoreThreshold", fraudProperties.getHighRiskScoreThreshold());
            }
            case "FRAUD:LARGE_TRANSACTION" -> {
                thresholds.put("scoreContribution", fraudProperties.getLargeTransactionScore());
                thresholds.put("amountThreshold", fraudProperties.getLargeTransactionThreshold());
            }
            case "FRAUD:HIGH_RISK_COUNTRY" -> {
                thresholds.put("scoreContribution", fraudProperties.getHighRiskCountryScore());
                thresholds.put("highRiskCountries", fraudProperties.getHighRiskCountries());
            }
            case "FRAUD:RAPID_MOVEMENT" -> {
                thresholds.put("scoreContribution", fraudProperties.getRapidMovementScore());
                thresholds.put("windowHours", fraudProperties.getRapidMovementWindowHours());
                thresholds.put(
                        "combinedAmountThreshold",
                        fraudProperties.getRapidMovementCombinedAmountThreshold()
                );
            }
            case "FRAUD:STRUCTURING" -> {
                thresholds.put("scoreContribution", fraudProperties.getStructuringScore());
                thresholds.put("windowDays", fraudProperties.getStructuringWindowDays());
                thresholds.put(
                        "reportingThreshold",
                        fraudProperties.getStructuringReportingThreshold()
                );
            }
            case "FRAUD:UNUSUAL_CHANNEL" -> {
                thresholds.put("scoreContribution", fraudProperties.getUnusualChannelScore());
                thresholds.put(
                        "minimumHistory",
                        fraudProperties.getUnusualChannelMinimumHistory()
                );
                thresholds.put(
                        "commonRatioThreshold",
                        fraudProperties.getUnusualChannelCommonRatio()
                );
            }
            case "FRAUD:CUSTOMER_PROFILE_MISMATCH" -> {
                thresholds.put(
                        "scoreContribution",
                        fraudProperties.getCustomerProfileMismatchScore()
                );
                thresholds.put(
                        "largeTransactionThreshold",
                        fraudProperties.getProfileMismatchLargeTransactionThreshold()
                );
                thresholds.put(
                        "newAccountDays",
                        fraudProperties.getProfileMismatchNewAccountDays()
                );
            }
            case "KYC:KYC_NOT_VERIFIED" ->
                    thresholds.put("scoreContribution", kycProperties.getKycNotVerifiedScore());
            case "KYC:MISSING_OCCUPATION" ->
                    thresholds.put("scoreContribution", kycProperties.getMissingOccupationScore());
            case "KYC:MISSING_SOURCE_OF_FUNDS" ->
                    thresholds.put("scoreContribution", kycProperties.getMissingSourceOfFundsScore());
            case "KYC:PEP_CUSTOMER" ->
                    thresholds.put("scoreContribution", kycProperties.getPepCustomerScore());
            case "KYC:HIGH_CUSTOMER_RISK" ->
                    thresholds.put("scoreContribution", kycProperties.getHighCustomerRiskScore());
            case "KYC:HIGH_RISK_NATIONALITY" -> {
                thresholds.put("scoreContribution", kycProperties.getRestrictedNationalityScore());
                thresholds.put("highRiskCountries", kycProperties.getHighRiskCountries());
            }
            case "KYC:HIGH_RISK_RESIDENCE" -> {
                thresholds.put("scoreContribution", kycProperties.getRestrictedResidenceScore());
                thresholds.put("highRiskCountries", kycProperties.getHighRiskCountries());
            }
            case "KYC:NEW_ACCOUNT" -> {
                thresholds.put("scoreContribution", kycProperties.getNewAccountScore());
                thresholds.put("newAccountDays", kycProperties.getNewAccountDays());
            }
            case "KYC:INACTIVE_OR_RESTRICTED_ACCOUNT" ->
                    thresholds.put("scoreContribution", kycProperties.getInactiveAccountScore());
            case "KYC:PROFILE_INCONSISTENCY" -> {
                thresholds.put("scoreContribution", kycProperties.getProfileInconsistencyScore());
                thresholds.put(
                        "highValueTransactionThreshold",
                        kycProperties.getHighValueTransactionThreshold()
                );
            }
            case "AML:STRUCTURING" -> {
                thresholds.put("scoreContribution", amlProperties.getStructuringScore());
                thresholds.put("reportingThreshold", amlProperties.getReportingThreshold());
            }
            case "AML:RAPID_MOVEMENT" -> {
                thresholds.put("scoreContribution", amlProperties.getRapidMovementScore());
                thresholds.put("windowHours", amlProperties.getRapidWindowHours());
                thresholds.put("combinedAmountThreshold", amlProperties.getRapidCombinedThreshold());
            }
            case "AML:LARGE_TRANSACTION" -> {
                thresholds.put("scoreContribution", amlProperties.getLargeTransactionScore());
                thresholds.put("amountThreshold", amlProperties.getLargeTransactionThreshold());
            }
            case "AML:HIGH_RISK_COUNTRY" -> {
                thresholds.put("scoreContribution", amlProperties.getHighRiskCountryScore());
                thresholds.put("highRiskCountries", amlProperties.getHighRiskCountries());
            }
            case "AML:HIGH_RISK_CUSTOMER" ->
                    thresholds.put("scoreContribution", amlProperties.getHighRiskCustomerScore());
            case "AML:PEP_ACTIVITY" ->
                    thresholds.put("scoreContribution", amlProperties.getPepActivityScore());
            case "AML:FLAGGED_TRANSACTION" ->
                    thresholds.put("scoreContribution", amlProperties.getFlaggedTransactionScore());
            case "AML:HIGH_TRANSACTION_RISK_SCORE" -> {
                thresholds.put("scoreContribution", amlProperties.getHighTransactionRiskScore());
                thresholds.put("riskScoreThreshold", amlProperties.getHighRiskScoreThreshold());
            }
            case "AML:NEW_ACCOUNT_ACTIVITY" -> {
                thresholds.put("scoreContribution", amlProperties.getNewAccountActivityScore());
                thresholds.put("newAccountDays", amlProperties.getNewAccountDays());
            }
            case "AML:MULTIPLE_HIGH_RISK_INDICATORS" ->
                    thresholds.put(
                            "scoreContribution",
                            amlProperties.getMultipleHighRiskIndicatorsScore()
                    );
            case "COMPLIANCE:MULTIPLE_HIGH_RISK_FINDINGS" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getMultipleHighRiskFindingsScore()
                    );
            case "COMPLIANCE:FRAUD_CRITICAL" ->
                    thresholds.put("scoreContribution", complianceProperties.getFraudCriticalScore());
            case "COMPLIANCE:AML_CRITICAL" ->
                    thresholds.put("scoreContribution", complianceProperties.getAmlCriticalScore());
            case "COMPLIANCE:KYC_CRITICAL" ->
                    thresholds.put("scoreContribution", complianceProperties.getKycCriticalScore());
            case "COMPLIANCE:FRAUD_AND_AML_COMBINATION" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getFraudAndAmlCombinationScore()
                    );
            case "COMPLIANCE:PEP_WITH_AML" ->
                    thresholds.put("scoreContribution", complianceProperties.getPepWithAmlScore());
            case "COMPLIANCE:HIGH_CONFIDENCE_MATCH" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getHighConfidenceMatchScore()
                    );
            case "COMPLIANCE:MULTIPLE_ESCALATIONS" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getMultipleEscalationsScore()
                    );
            case "COMPLIANCE:CONSISTENT_HIGH_RISK_PATTERN" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getConsistentHighRiskPatternScore()
                    );
            case "COMPLIANCE:MANUAL_REVIEW_REQUIRED" ->
                    thresholds.put(
                            "scoreContribution",
                            complianceProperties.getManualReviewRequiredScore()
                    );
            default -> {
            }
        }

        return Map.copyOf(thresholds);
    }
}
