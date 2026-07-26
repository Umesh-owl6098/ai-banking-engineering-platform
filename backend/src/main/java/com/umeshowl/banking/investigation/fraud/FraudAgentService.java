package com.umeshowl.banking.investigation.fraud;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FraudAgentService {

    private final InvestigationCaseService investigationCaseService;
    private final MockTransactionRepository transactionRepository;
    private final FraudAgentProperties properties;

    public FraudAgentService(
            InvestigationCaseService investigationCaseService,
            MockTransactionRepository transactionRepository,
            FraudAgentProperties properties
    ) {
        this.investigationCaseService = investigationCaseService;
        this.transactionRepository = transactionRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public FraudAnalysisResult analyze(UUID investigationId) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        MockTransaction transaction = investigationCase.getTransaction();
        MockCustomer customer = investigationCase.getCustomer();

        if (customer == null && transaction != null) {
            customer = transaction.getCustomer();
        }

        List<MockTransaction> history = transaction == null
                ? List.of()
                : getTransactionHistory(customer, transaction);
        List<FraudIndicator> indicators = new ArrayList<>();

        if (transaction != null) {
            evaluateTransactionIndicators(
                    transaction,
                    customer,
                    history,
                    indicators
            );
        }

        int totalScore = indicators.stream()
                .mapToInt(FraudIndicator::scoreContribution)
                .sum();
        totalScore = Math.clamp(totalScore, 0, 100);
        FraudRiskLevel riskLevel = toRiskLevel(totalScore);

        return new FraudAnalysisResult(
                investigationCase.getId(),
                customer == null ? null : customer.getId(),
                transaction == null ? null : transaction.getId(),
                totalScore,
                riskLevel,
                createSummary(indicators, totalScore, riskLevel),
                indicators,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<MockTransaction> getTransactionHistory(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        if (customer == null || customer.getId() == null) {
            return List.of(transaction);
        }

        List<MockTransaction> history =
                new ArrayList(
                        transactionRepository
                                .findByCustomer_IdOrderByTransactionDateDesc(
                                        customer.getId()
                                )
                );

        if (history.stream().noneMatch(item ->
                transaction.getId() != null
                        && transaction.getId().equals(item.getId())
        )) {
            history.add(transaction);
        }

        return history;
    }

    private void evaluateTransactionIndicators(
            MockTransaction transaction,
            MockCustomer customer,
            List<MockTransaction> history,
            List<FraudIndicator> indicators
    ) {
        if (transaction.isFlagged()) {
            add(indicators, FraudIndicatorType.FLAGGED_TRANSACTION,
                    properties.getFlaggedTransactionScore(),
                    "Transaction has been explicitly flagged",
                    Map.of("flagged", true), List.of(transaction.getId()));
        }

        if (atLeast(transaction.getRiskScore(),
                properties.getHighRiskScoreThreshold())) {
            add(indicators, FraudIndicatorType.HIGH_TRANSACTION_RISK_SCORE,
                    properties.getHighTransactionRiskScore(),
                    "Transaction risk score meets the configured threshold",
                    Map.of(
                            "riskScore", transaction.getRiskScore(),
                            "threshold",
                            properties.getHighRiskScoreThreshold()
                    ), List.of(transaction.getId()));
        }

        if (atLeast(transaction.getAmount(),
                properties.getLargeTransactionThreshold())) {
            add(indicators, FraudIndicatorType.LARGE_TRANSACTION,
                    properties.getLargeTransactionScore(),
                    "Transaction amount meets the configured threshold",
                    Map.of(
                            "amount", transaction.getAmount(),
                            "threshold",
                            properties.getLargeTransactionThreshold()
                    ), List.of(transaction.getId()));
        }

        evaluateHighRiskCountry(transaction, indicators);
        evaluateRapidMovement(transaction, history, indicators);
        evaluateStructuring(transaction, history, indicators);
        evaluateUnusualChannel(transaction, history, indicators);
        evaluateProfileMismatch(transaction, customer, indicators);
    }

    private void evaluateHighRiskCountry(
            MockTransaction transaction,
            List<FraudIndicator> indicators
    ) {
        List<String> matchedCountries = new ArrayList<>();

        if (isHighRiskCountry(transaction.getOriginCountry())) {
            matchedCountries.add(transaction.getOriginCountry());
        }

        if (isHighRiskCountry(transaction.getDestinationCountry())
                && !matchedCountries.contains(
                        transaction.getDestinationCountry()
                )) {
            matchedCountries.add(transaction.getDestinationCountry());
        }

        if (!matchedCountries.isEmpty()) {
            add(indicators, FraudIndicatorType.HIGH_RISK_COUNTRY,
                    properties.getHighRiskCountryScore(),
                    "Transaction involves a configured high-risk country",
                    Map.of("matchedCountries", matchedCountries),
                    List.of(transaction.getId()));
        }
    }

    private void evaluateRapidMovement(
            MockTransaction transaction,
            List<MockTransaction> history,
            List<FraudIndicator> indicators
    ) {
        List<MockTransaction> windowTransactions = inWindow(
                history,
                transaction.getTransactionDate(),
                properties.getRapidMovementWindowHours()
        );
        BigDecimal combinedAmount = totalAmount(windowTransactions);

        if (windowTransactions.size() >= 2
                && greaterThan(
                        combinedAmount,
                        properties
                                .getRapidMovementCombinedAmountThreshold()
                )) {
            add(indicators, FraudIndicatorType.RAPID_MOVEMENT,
                    properties.getRapidMovementScore(),
                    "Multiple customer transactions moved funds within "
                            + "the configured time window",
                    Map.of(
                            "windowHours",
                            properties.getRapidMovementWindowHours(),
                            "combinedAmount",
                            combinedAmount,
                            "threshold",
                            properties
                                    .getRapidMovementCombinedAmountThreshold()
                    ), transactionIds(windowTransactions));
        }
    }

    private void evaluateStructuring(
            MockTransaction transaction,
            List<MockTransaction> history,
            List<FraudIndicator> indicators
    ) {
        List<MockTransaction> structuredTransactions = inWindow(
                history,
                transaction.getTransactionDate(),
                properties.getStructuringWindowDays() * 24
        ).stream().filter(item -> item.getAmount() != null
                && item.getAmount().compareTo(
                        properties.getStructuringReportingThreshold()
                ) < 0).toList();
        BigDecimal combinedAmount = totalAmount(structuredTransactions);

        if (structuredTransactions.size() >= 2
                && greaterThan(
                        combinedAmount,
                        properties.getStructuringReportingThreshold()
                )) {
            add(indicators, FraudIndicatorType.STRUCTURING,
                    properties.getStructuringScore(),
                    "Multiple below-reporting-threshold transactions "
                            + "exceed the reporting threshold in aggregate",
                    Map.of(
                            "windowDays",
                            properties.getStructuringWindowDays(),
                            "combinedAmount",
                            combinedAmount,
                            "reportingThreshold",
                            properties.getStructuringReportingThreshold()
                    ), transactionIds(structuredTransactions));
        }
    }

    private void evaluateUnusualChannel(
            MockTransaction transaction,
            List<MockTransaction> history,
            List<FraudIndicator> indicators
    ) {
        List<MockTransaction> priorTransactions = history.stream()
                .filter(item -> item.getId() == null
                        || !item.getId().equals(transaction.getId()))
                .filter(item -> item.getChannel() != null)
                .toList();

        if (priorTransactions.size()
                < properties.getUnusualChannelMinimumHistory()) {
            return;
        }

        Map<String, Long> channelCounts = new HashMap<>();
        for (MockTransaction item : priorTransactions) {
            channelCounts.merge(
                    normalize(item.getChannel()),
                    1L,
                    Long::sum
            );
        }

        Map.Entry<String, Long> commonChannel = channelCounts.entrySet()
                .stream()
                .max((left, right) -> Long.compare(
                        left.getValue(),
                        right.getValue()
                ))
                .orElseThrow();
        BigDecimal commonRatio = BigDecimal.valueOf(
                commonChannel.getValue()
        ).divide(
                BigDecimal.valueOf(priorTransactions.size()),
                2,
                java.math.RoundingMode.HALF_UP
        );

        if (!normalize(transaction.getChannel()).equals(
                commonChannel.getKey()
        ) && atLeast(
                commonRatio,
                properties.getUnusualChannelCommonRatio()
        )) {
            add(indicators, FraudIndicatorType.UNUSUAL_CHANNEL,
                    properties.getUnusualChannelScore(),
                    "Transaction channel differs from the customer's "
                            + "dominant historical channel",
                    Map.of(
                            "transactionChannel",
                            transaction.getChannel(),
                            "commonChannel", commonChannel.getKey(),
                            "commonChannelRatio", commonRatio,
                            "historyCount", priorTransactions.size()
                    ), List.of(transaction.getId()));
        }
    }

    private void evaluateProfileMismatch(
            MockTransaction transaction,
            MockCustomer customer,
            List<FraudIndicator> indicators
    ) {
        if (customer == null || !atLeast(
                transaction.getAmount(),
                properties.getProfileMismatchLargeTransactionThreshold()
        )) {
            return;
        }

        List<String> assumptions = new ArrayList<>();

        if ("LOW".equalsIgnoreCase(customer.getRiskRating())) {
            assumptions.add("customer is rated LOW risk");
        }

        if (isBlank(customer.getOccupation())) {
            assumptions.add("occupation is not recorded");
        }

        if (isBlank(customer.getSourceOfFunds())) {
            assumptions.add("source of funds is not recorded");
        }

        if (isNewAccount(customer, transaction)) {
            assumptions.add("account is newly opened");
        }

        if (!assumptions.isEmpty()) {
            add(indicators, FraudIndicatorType.CUSTOMER_PROFILE_MISMATCH,
                    properties.getCustomerProfileMismatchScore(),
                    "High-value transaction is inconsistent with the "
                            + "configured customer-profile assumptions: "
                            + String.join(", ", assumptions),
                    Map.of(
                            "amount", transaction.getAmount(),
                            "threshold",
                            properties
                                    .getProfileMismatchLargeTransactionThreshold(),
                            "assumptions", assumptions
                    ), List.of(transaction.getId()));
        }
    }

    private boolean isNewAccount(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        return customer.getAccountOpened() != null
                && transaction.getTransactionDate() != null
                && !transaction.getTransactionDate().toLocalDate()
                        .isAfter(
                                customer.getAccountOpened().plusDays(
                                        properties
                                                .getProfileMismatchNewAccountDays()
                                )
                        );
    }

    private List<MockTransaction> inWindow(
            List<MockTransaction> history,
            OffsetDateTime referenceTime,
            int windowHours
    ) {
        if (referenceTime == null) {
            return List.of();
        }

        OffsetDateTime start = referenceTime.minusHours(windowHours);

        return history.stream()
                .filter(item -> item.getTransactionDate() != null)
                .filter(item -> !item.getTransactionDate().isBefore(start))
                .filter(item -> !item.getTransactionDate()
                        .isAfter(referenceTime))
                .toList();
    }

    private BigDecimal totalAmount(List<MockTransaction> transactions) {
        return transactions.stream()
                .map(MockTransaction::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<UUID> transactionIds(
            List<MockTransaction> transactions
    ) {
        return transactions.stream()
                .map(MockTransaction::getId)
                .filter(id -> id != null)
                .toList();
    }

    private boolean isHighRiskCountry(String country) {
        return country != null && properties.getHighRiskCountries()
                .stream()
                .map(this::normalize)
                .anyMatch(normalize(country)::equals);
    }

    private boolean atLeast(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null
                && value.compareTo(threshold) >= 0;
    }

    private boolean greaterThan(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null
                && value.compareTo(threshold) > 0;
    }

    private FraudRiskLevel toRiskLevel(int score) {
        if (score >= 80) {
            return FraudRiskLevel.CRITICAL;
        }
        if (score >= 60) {
            return FraudRiskLevel.HIGH;
        }
        if (score >= 30) {
            return FraudRiskLevel.MEDIUM;
        }
        return FraudRiskLevel.LOW;
    }

    private String createSummary(
            List<FraudIndicator> indicators,
            int score,
            FraudRiskLevel riskLevel
    ) {
        if (indicators.isEmpty()) {
            return "No deterministic fraud indicators were triggered.";
        }

        return indicators.size() + " deterministic fraud indicators "
                + "triggered; score " + score + " (" + riskLevel + ").";
    }

    private void add(
            List<FraudIndicator> indicators,
            FraudIndicatorType type,
            int score,
            String explanation,
            Map<String, Object> evidence,
            List<UUID> transactionIds
    ) {
        indicators.add(new FraudIndicator(
                type,
                score,
                explanation,
                evidence,
                transactionIds
        ));
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
