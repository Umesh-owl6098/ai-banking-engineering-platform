package com.umeshowl.banking.screening;

import com.umeshowl.banking.investigation.fraud.FraudAgentProperties;
import com.umeshowl.banking.investigation.kyc.KycAgentProperties;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TransactionScreeningService {

    private static final Logger log = LoggerFactory.getLogger(
            TransactionScreeningService.class
    );

    private final MockTransactionRepository transactionRepository;
    private final TransactionScreeningResultRepository screeningResultRepository;
    private final FraudAgentProperties fraudProperties;
    private final KycAgentProperties kycProperties;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionScreeningService(
            MockTransactionRepository transactionRepository,
            TransactionScreeningResultRepository screeningResultRepository,
            FraudAgentProperties fraudProperties,
            KycAgentProperties kycProperties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transactionRepository = transactionRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.fraudProperties = fraudProperties;
        this.kycProperties = kycProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransactionScreeningResult beginProcessing(
            MockTransaction transaction
    ) {
        TransactionScreeningResult result = new TransactionScreeningResult();
        result.setTransaction(transaction);
        result.setStatus(TransactionScreeningStatus.PROCESSING);
        result.setScreeningScore(BigDecimal.ZERO);
        result.setTriggeredRules(new String[0]);
        result.setReason("Screening in progress");
        return screeningResultRepository.save(result);
    }

    @Transactional
    public TransactionScreeningResult screen(
            TransactionScreeningResult processingResult
    ) {
        MockTransaction transaction = processingResult.getTransaction();

        try {
            MockCustomer customer = transaction.getCustomer();
            List<MockTransaction> history = loadHistory(transaction, customer);
            List<RuleEvaluation> evaluations = evaluateRules(
                    transaction,
                    customer,
                    history
            );

            int totalScore = evaluations.stream()
                    .mapToInt(RuleEvaluation::scoreContribution)
                    .sum();
            totalScore = Math.clamp(totalScore, 0, 100);

            Set<String> triggeredRuleNames = new LinkedHashSet<>();
            for (RuleEvaluation evaluation : evaluations) {
                triggeredRuleNames.add(evaluation.rule().name());
            }

            TransactionScreeningStatus status = resolveStatus(
                    totalScore,
                    evaluations
            );
            String reason = buildReason(status, evaluations, totalScore);

            processingResult.setStatus(status);
            processingResult.setScreeningScore(
                    BigDecimal.valueOf(totalScore)
            );
            processingResult.setTriggeredRules(
                    triggeredRuleNames.toArray(String[]::new)
            );
            processingResult.setReason(reason);
            processingResult.setScreenedAt(
                    OffsetDateTime.now(ZoneOffset.UTC)
            );

            TransactionScreeningResult saved =
                    screeningResultRepository.save(processingResult);

            log.info(
                    "transaction_screening_completed transactionId={} status={} score={} rules={}",
                    transaction.getId(),
                    status,
                    totalScore,
                    triggeredRuleNames
            );

            publishCompletedEvent(saved);
            return saved;
        } catch (RuntimeException exception) {
            processingResult.setStatus(
                    TransactionScreeningStatus.SCREENING_FAILED
            );
            processingResult.setReason(
                    "Screening failed: " + exception.getMessage()
            );
            processingResult.setScreenedAt(
                    OffsetDateTime.now(ZoneOffset.UTC)
            );

            log.warn(
                    "transaction_screening_failed transactionId={} message={}",
                    transaction.getId(),
                    exception.getMessage()
            );

            TransactionScreeningResult saved =
                    screeningResultRepository.save(processingResult);
            publishCompletedEvent(saved);
            return saved;
        }
    }

    private void publishCompletedEvent(TransactionScreeningResult result) {
        try {
            eventPublisher.publishEvent(new ScreeningCompletedEvent(result));
        } catch (RuntimeException exception) {
            log.error(
                    "screening_completed_event_failed transactionId={} message={}",
                    result.getTransaction().getId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private List<MockTransaction> loadHistory(
            MockTransaction transaction,
            MockCustomer customer
    ) {
        if (customer == null || customer.getId() == null) {
            return List.of(transaction);
        }

        List<MockTransaction> history = new ArrayList<>(
                transactionRepository.findByCustomer_IdOrderByTransactionDateDesc(
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

    private List<RuleEvaluation> evaluateRules(
            MockTransaction transaction,
            MockCustomer customer,
            List<MockTransaction> history
    ) {
        List<RuleEvaluation> evaluations = new ArrayList<>();

        if (transaction.isFlagged()) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.FLAGGED_STATUS,
                    fraudProperties.getFlaggedTransactionScore(),
                    "Transaction is flagged"
            ));
        }

        if (atLeast(
                transaction.getRiskScore(),
                fraudProperties.getHighRiskScoreThreshold()
        )) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.HIGH_RISK_SCORE,
                    fraudProperties.getHighTransactionRiskScore(),
                    "Risk score meets screening threshold"
            ));
        }

        if (atLeast(
                transaction.getAmount(),
                fraudProperties.getLargeTransactionThreshold()
        )) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.LARGE_TRANSFER,
                    fraudProperties.getLargeTransactionScore(),
                    "Amount meets large-transfer threshold"
            ));
        }

        evaluateHighRiskCountry(transaction, evaluations);
        evaluateRapidMovement(transaction, history, evaluations);
        evaluateStructuring(transaction, history, evaluations);
        evaluatePepActivity(transaction, customer, evaluations);
        evaluateNewAccountActivity(transaction, customer, evaluations);

        return evaluations;
    }

    private void evaluateHighRiskCountry(
            MockTransaction transaction,
            List<RuleEvaluation> evaluations
    ) {
        boolean originMatch = isHighRiskCountry(
                transaction.getOriginCountry()
        );
        boolean destinationMatch = isHighRiskCountry(
                transaction.getDestinationCountry()
        );

        if (originMatch || destinationMatch) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.HIGH_RISK_COUNTRY,
                    fraudProperties.getHighRiskCountryScore(),
                    "Transaction involves a high-risk country"
            ));
        }
    }

    private void evaluateRapidMovement(
            MockTransaction transaction,
            List<MockTransaction> history,
            List<RuleEvaluation> evaluations
    ) {
        List<MockTransaction> windowTransactions = inWindow(
                history,
                transaction.getTransactionDate(),
                fraudProperties.getRapidMovementWindowHours()
        );
        BigDecimal combinedAmount = totalAmount(windowTransactions);

        if (windowTransactions.size() >= 2
                && greaterThan(
                        combinedAmount,
                        fraudProperties
                                .getRapidMovementCombinedAmountThreshold()
                )) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.RAPID_MOVEMENT,
                    fraudProperties.getRapidMovementScore(),
                    "Multiple transactions exceed rapid-movement threshold"
            ));
        }
    }

    private void evaluateStructuring(
            MockTransaction transaction,
            List<MockTransaction> history,
            List<RuleEvaluation> evaluations
    ) {
        List<MockTransaction> structuredTransactions = inWindow(
                history,
                transaction.getTransactionDate(),
                fraudProperties.getStructuringWindowDays() * 24
        ).stream()
                .filter(item -> item.getAmount() != null
                        && item.getAmount().compareTo(
                                fraudProperties
                                        .getStructuringReportingThreshold()
                        ) < 0)
                .toList();
        BigDecimal combinedAmount = totalAmount(structuredTransactions);

        if (structuredTransactions.size() >= 2
                && greaterThan(
                        combinedAmount,
                        fraudProperties.getStructuringReportingThreshold()
                )) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.STRUCTURING,
                    fraudProperties.getStructuringScore(),
                    "Below-threshold deposits exceed reporting limit in aggregate"
            ));
        }
    }

    private void evaluatePepActivity(
            MockTransaction transaction,
            MockCustomer customer,
            List<RuleEvaluation> evaluations
    ) {
        if (customer == null || customer.getPepStatus() == null) {
            return;
        }

        if (!"NONE".equalsIgnoreCase(customer.getPepStatus())) {
            evaluations.add(new RuleEvaluation(
                    TransactionScreeningRule.PEP_ACTIVITY,
                    kycProperties.getPepCustomerScore(),
                    "Customer has PEP status"
            ));
        }
    }

    private void evaluateNewAccountActivity(
            MockTransaction transaction,
            MockCustomer customer,
            List<RuleEvaluation> evaluations
    ) {
        if (customer == null || !isNewAccount(customer, transaction)) {
            return;
        }

        evaluations.add(new RuleEvaluation(
                TransactionScreeningRule.NEW_ACCOUNT_ACTIVITY,
                kycProperties.getNewAccountScore(),
                "Account opened within new-account monitoring window"
        ));
    }

    private TransactionScreeningStatus resolveStatus(
            int totalScore,
            List<RuleEvaluation> evaluations
    ) {
        if (evaluations.isEmpty()) {
            return TransactionScreeningStatus.CLEARED;
        }

        if (totalScore >= 80) {
            return TransactionScreeningStatus.CRITICAL;
        }

        return TransactionScreeningStatus.SUSPICIOUS;
    }

    private String buildReason(
            TransactionScreeningStatus status,
            List<RuleEvaluation> evaluations,
            int totalScore
    ) {
        return switch (status) {
            case CLEARED -> "No screening rules triggered";
            case CRITICAL -> evaluations.size()
                    + " rules triggered; screening score "
                    + totalScore + " (critical)";
            case SUSPICIOUS -> evaluations.size()
                    + " rules triggered; screening score "
                    + totalScore + " (suspicious)";
            case SCREENING_FAILED -> "Screening could not be completed";
            case PROCESSING -> "Screening in progress";
        };
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

    private boolean isNewAccount(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        return customer.getAccountOpened() != null
                && transaction.getTransactionDate() != null
                && !transaction.getTransactionDate().toLocalDate()
                        .isAfter(
                                customer.getAccountOpened().plusDays(
                                        kycProperties.getNewAccountDays()
                                )
                        );
    }

    private boolean isHighRiskCountry(String country) {
        return country != null
                && fraudProperties.getHighRiskCountries()
                        .stream()
                        .map(this::normalize)
                        .anyMatch(normalize(country)::equals);
    }

    private boolean atLeast(BigDecimal value, BigDecimal threshold) {
        return value != null
                && threshold != null
                && value.compareTo(threshold) >= 0;
    }

    private boolean greaterThan(BigDecimal value, BigDecimal threshold) {
        return value != null
                && threshold != null
                && value.compareTo(threshold) > 0;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private record RuleEvaluation(
            TransactionScreeningRule rule,
            int scoreContribution,
            String detail
    ) {
    }
}
