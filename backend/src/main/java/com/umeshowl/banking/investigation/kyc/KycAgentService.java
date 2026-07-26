package com.umeshowl.banking.investigation.kyc;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class KycAgentService {

    private final InvestigationCaseService investigationCaseService;
    private final KycAgentProperties properties;

    public KycAgentService(
            InvestigationCaseService investigationCaseService,
            KycAgentProperties properties
    ) {
        this.investigationCaseService = investigationCaseService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public KycAnalysisResult analyze(UUID investigationId) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        MockTransaction transaction = investigationCase.getTransaction();
        MockCustomer customer = investigationCase.getCustomer();

        if (customer == null && transaction != null) {
            customer = transaction.getCustomer();
        }

        List<KycIndicator> indicators = new ArrayList<>();
        if (customer != null) {
            evaluate(customer, transaction, indicators);
        }

        int score = Math.clamp(
                indicators.stream()
                        .mapToInt(KycIndicator::scoreContribution)
                        .sum(),
                0,
                100
        );
        KycRiskLevel riskLevel = riskLevel(score);

        return new KycAnalysisResult(
                investigationCase.getId(),
                customer == null ? null : customer.getId(),
                transaction == null ? null : transaction.getId(),
                score,
                riskLevel,
                summary(indicators, score, riskLevel),
                indicators,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void evaluate(
            MockCustomer customer,
            MockTransaction transaction,
            List<KycIndicator> indicators
    ) {
        if (!isVerified(customer.getKycStatus())) {
            add(indicators, KycIndicatorType.KYC_NOT_VERIFIED,
                    properties.getKycNotVerifiedScore(),
                    "Customer KYC status is not VERIFIED or APPROVED",
                    Map.of("kycStatus", safe(customer.getKycStatus())));
        }
        if (isUnknown(customer.getOccupation())) {
            add(indicators, KycIndicatorType.MISSING_OCCUPATION,
                    properties.getMissingOccupationScore(),
                    "Customer occupation is missing or unknown",
                    Map.of("occupation", safe(customer.getOccupation())));
        }
        if (isUnknown(customer.getSourceOfFunds())) {
            add(indicators, KycIndicatorType.MISSING_SOURCE_OF_FUNDS,
                    properties.getMissingSourceOfFundsScore(),
                    "Customer source of funds is missing or unknown",
                    Map.of("sourceOfFunds",
                            safe(customer.getSourceOfFunds())));
        }
        if ("PEP".equalsIgnoreCase(customer.getPepStatus())) {
            add(indicators, KycIndicatorType.PEP_CUSTOMER,
                    properties.getPepCustomerScore(),
                    "Customer is a politically exposed person",
                    Map.of("pepStatus", customer.getPepStatus()));
        }
        if ("HIGH".equalsIgnoreCase(customer.getRiskRating())) {
            add(indicators, KycIndicatorType.HIGH_CUSTOMER_RISK,
                    properties.getHighCustomerRiskScore(),
                    "Customer risk rating is HIGH",
                    Map.of("riskRating", customer.getRiskRating()));
        }
        if (restricted(customer.getNationality())) {
            add(indicators,
                    KycIndicatorType.HIGH_RISK_NATIONALITY,
                    properties.getRestrictedNationalityScore(),
                    "Customer nationality is in the restricted country list",
                    Map.of("nationality",
                            safe(customer.getNationality())));
        }
        if (restricted(customer.getCountryOfResidence())) {
            add(indicators,
                    KycIndicatorType.HIGH_RISK_RESIDENCE,
                    properties.getRestrictedResidenceScore(),
                    "Customer residence is in the restricted country list",
                    Map.of("countryOfResidence",
                            safe(customer.getCountryOfResidence())));
        }
        if (isNewAccount(customer, transaction)) {
            add(indicators, KycIndicatorType.NEW_ACCOUNT,
                    properties.getNewAccountScore(),
                    "Account was opened within the configured review period",
                    Map.of(
                            "accountOpened", customer.getAccountOpened(),
                            "newAccountDays",
                            properties.getNewAccountDays()
                    ));
        }
        if (!"ACTIVE".equalsIgnoreCase(customer.getAccountStatus())) {
            add(indicators,
                    KycIndicatorType.INACTIVE_OR_RESTRICTED_ACCOUNT,
                    properties.getInactiveAccountScore(),
                    "Customer account status is not ACTIVE",
                    Map.of("accountStatus",
                            safe(customer.getAccountStatus())));
        }

        evaluateProfileInconsistency(customer, transaction, indicators);
    }

    private void evaluateProfileInconsistency(
            MockCustomer customer,
            MockTransaction transaction,
            List<KycIndicator> indicators
    ) {
        List<String> assumptions = new ArrayList<>();
        boolean missingSource = isUnknown(customer.getSourceOfFunds());
        boolean incompleteKyc = !isVerified(customer.getKycStatus());

        if ("HIGH".equalsIgnoreCase(customer.getRiskRating())
                && missingSource) {
            assumptions.add("HIGH risk customer has missing source of funds");
        }
        if ("PEP".equalsIgnoreCase(customer.getPepStatus())
                && incompleteKyc) {
            assumptions.add("PEP customer has incomplete KYC");
        }
        if (transaction != null && isNewAccount(customer, transaction)
                && (transaction.isFlagged() || atLeast(
                        transaction.getAmount(),
                        properties.getHighValueTransactionThreshold()
                ))) {
            assumptions.add(
                    "new account has a flagged or high-value transaction"
            );
        }
        if (transaction != null
                && !"ACTIVE".equalsIgnoreCase(
                        customer.getAccountStatus()
                )
                && isRecentActivity(transaction)) {
            assumptions.add(
                    "inactive or restricted account has recent activity"
            );
        }

        if (!assumptions.isEmpty()) {
            add(indicators, KycIndicatorType.PROFILE_INCONSISTENCY,
                    properties.getProfileInconsistencyScore(),
                    "Configured deterministic profile assumptions are met: "
                            + String.join("; ", assumptions),
                    Map.of("assumptions", assumptions));
        }
    }

    private boolean isNewAccount(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        if (customer.getAccountOpened() == null) {
            return false;
        }
        LocalDate referenceDate = transaction == null
                || transaction.getTransactionDate() == null
                ? OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
                : transaction.getTransactionDate().toLocalDate();

        return !customer.getAccountOpened().isAfter(referenceDate)
                && !customer.getAccountOpened().plusDays(
                        properties.getNewAccountDays()
                ).isBefore(referenceDate);
    }

    private boolean isVerified(String status) {
        return "VERIFIED".equalsIgnoreCase(status)
                || "APPROVED".equalsIgnoreCase(status);
    }

    private boolean isUnknown(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty()
                || normalized.equals("UNKNOWN")
                || normalized.equals("N/A")
                || normalized.equals("NOT PROVIDED");
    }

    private boolean restricted(String country) {
        return country != null && properties.getHighRiskCountries()
                .stream()
                .map(this::normalize)
                .anyMatch(normalize(country)::equals);
    }

    private boolean atLeast(BigDecimal amount, BigDecimal threshold) {
        return amount != null && threshold != null
                && amount.compareTo(threshold) >= 0;
    }

    private boolean isRecentActivity(MockTransaction transaction) {
        return transaction.getTransactionDate() != null
                && !transaction.getTransactionDate()
                        .isBefore(
                                OffsetDateTime.now(ZoneOffset.UTC)
                                        .minusDays(
                                                properties
                                                        .getRecentActivityDays()
                                        )
                        );
    }

    private KycRiskLevel riskLevel(int score) {
        if (score >= 80) return KycRiskLevel.CRITICAL;
        if (score >= 60) return KycRiskLevel.HIGH;
        if (score >= 30) return KycRiskLevel.MEDIUM;
        return KycRiskLevel.LOW;
    }

    private String summary(
            List<KycIndicator> indicators,
            int score,
            KycRiskLevel riskLevel
    ) {
        return indicators.isEmpty()
                ? "No deterministic KYC indicators were triggered."
                : indicators.size() + " deterministic KYC indicators "
                        + "triggered; score " + score + " ("
                        + riskLevel + ").";
    }

    private void add(
            List<KycIndicator> indicators,
            KycIndicatorType type,
            int score,
            String explanation,
            Map<String, Object> evidence
    ) {
        indicators.add(new KycIndicator(
                type, score, explanation, evidence
        ));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
