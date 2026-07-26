package com.umeshowl.banking.investigation.fraud;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudAgentServiceTest {

    private static final UUID CASE_ID = UUID.fromString(
            "40000000-0000-4000-8000-000000000001"
    );
    private static final UUID CUSTOMER_ID = UUID.fromString(
            "40000000-0000-4000-8000-000000000002"
    );
    private static final OffsetDateTime ANALYSIS_TIME =
            OffsetDateTime.of(
                    2026, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC
            );

    private InvestigationCaseService caseService;
    private MockTransactionRepository transactionRepository;
    private FraudAgentProperties properties;
    private FraudAgentService fraudAgentService;

    @BeforeEach
    void setUp() {
        caseService = mock(InvestigationCaseService.class);
        transactionRepository = mock(MockTransactionRepository.class);
        properties = new FraudAgentProperties();
        fraudAgentService = new FraudAgentService(
                caseService,
                transactionRepository,
                properties
        );
    }

    @Test
    void triggersFlaggedTransaction() {
        MockTransaction transaction = transaction(
                true, "100.00", "1.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertHas(result, FraudIndicatorType.FLAGGED_TRANSACTION);
    }

    @Test
    void triggersHighTransactionRiskScoreAtThreshold() {
        MockTransaction transaction = transaction(
                false, "100.00", "75.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertHas(
                result,
                FraudIndicatorType.HIGH_TRANSACTION_RISK_SCORE
        );
    }

    @Test
    void triggersLargeTransactionAtThreshold() {
        MockTransaction transaction = transaction(
                false, "10000.00", "1.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertHas(result, FraudIndicatorType.LARGE_TRANSACTION);
    }

    @Test
    void triggersHighRiskCountryForDestination() {
        MockTransaction transaction = transaction(
                false, "100.00", "1.00", "ONLINE"
        );
        transaction.setDestinationCountry("Iran");

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertHas(result, FraudIndicatorType.HIGH_RISK_COUNTRY);
    }

    @Test
    void triggersRapidMovementForMultipleLargeTransactions() {
        MockTransaction transaction = transaction(
                false, "11000.00", "1.00", "ONLINE"
        );
        MockTransaction previous = transaction(
                false, "11000.00", "1.00", "ONLINE"
        );
        previous.setTransactionDate(ANALYSIS_TIME.minusHours(2));

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction, previous)
        );

        assertHas(result, FraudIndicatorType.RAPID_MOVEMENT);
    }

    @Test
    void triggersStructuringForBelowThresholdTransactions() {
        MockTransaction transaction = transaction(
                false, "6000.00", "1.00", "ONLINE"
        );
        MockTransaction previous = transaction(
                false, "6000.00", "1.00", "ONLINE"
        );
        previous.setTransactionDate(ANALYSIS_TIME.minusDays(2));

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction, previous)
        );

        FraudIndicator indicator = indicator(
                result,
                FraudIndicatorType.STRUCTURING
        );
        assertTrue(
                indicator.relatedTransactionIds().containsAll(
                        List.of(transaction.getId(), previous.getId())
                )
        );
    }

    @Test
    void triggersUnusualChannelAgainstDominantHistory() {
        MockTransaction transaction = transaction(
                false, "100.00", "1.00", "CARD"
        );
        List<MockTransaction> history = List.of(
                transaction,
                transaction(false, "50.00", "1.00", "ONLINE"),
                transaction(false, "50.00", "1.00", "ONLINE"),
                transaction(false, "50.00", "1.00", "ONLINE")
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                history
        );

        assertHas(result, FraudIndicatorType.UNUSUAL_CHANNEL);
    }

    @Test
    void triggersCustomerProfileMismatchForLowRiskHighValueActivity() {
        MockTransaction transaction = transaction(
                false, "25000.00", "1.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("LOW", null, null),
                transaction,
                List.of(transaction)
        );

        FraudIndicator indicator = indicator(
                result,
                FraudIndicatorType.CUSTOMER_PROFILE_MISMATCH
        );
        assertTrue(indicator.explanation().contains("LOW risk"));
    }

    @Test
    void combinesScoresAndAssignsMediumRisk() {
        MockTransaction transaction = transaction(
                true, "10000.00", "75.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertEquals(50, result.totalScore());
        assertEquals(FraudRiskLevel.MEDIUM, result.riskLevel());
    }

    @Test
    void assignsHighRiskAtSixtyPoints() {
        properties.setFlaggedTransactionScore(60);
        MockTransaction transaction = transaction(
                true, "100.00", "1.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertEquals(60, result.totalScore());
        assertEquals(FraudRiskLevel.HIGH, result.riskLevel());
    }

    @Test
    void clampsCombinedScoreAndAssignsCriticalRisk() {
        properties.setFlaggedTransactionScore(80);
        properties.setHighTransactionRiskScore(60);
        MockTransaction transaction = transaction(
                true, "100.00", "75.00", "ONLINE"
        );

        FraudAnalysisResult result = analyze(
                customer("HIGH", "Analyst", "Salary"),
                transaction,
                List.of(transaction)
        );

        assertEquals(100, result.totalScore());
        assertEquals(FraudRiskLevel.CRITICAL, result.riskLevel());
    }

    @Test
    void handlesCustomerOnlyCasesWithoutTransactionIndicators() {
        MockCustomer customer = customer("HIGH", "Analyst", "Salary");
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(CASE_ID);
        investigationCase.setCustomer(customer);
        when(caseService.getCase(CASE_ID)).thenReturn(
                investigationCase
        );

        FraudAnalysisResult result = fraudAgentService.analyze(CASE_ID);

        assertEquals(CUSTOMER_ID, result.customerId());
        assertEquals(null, result.transactionId());
        assertEquals(0, result.totalScore());
        assertEquals(FraudRiskLevel.LOW, result.riskLevel());
    }

    private FraudAnalysisResult analyze(
            MockCustomer customer,
            MockTransaction transaction,
            List<MockTransaction> history
    ) {
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(CASE_ID);
        investigationCase.setCustomer(customer);
        investigationCase.setTransaction(transaction);
        transaction.setCustomer(customer);

        when(caseService.getCase(CASE_ID)).thenReturn(
                investigationCase
        );
        when(transactionRepository
                .findByCustomer_IdOrderByTransactionDateDesc(
                        CUSTOMER_ID
                ))
                .thenReturn(history);

        return fraudAgentService.analyze(CASE_ID);
    }

    private MockCustomer customer(
            String riskRating,
            String occupation,
            String sourceOfFunds
    ) {
        MockCustomer customer = new MockCustomer();
        customer.setId(CUSTOMER_ID);
        customer.setRiskRating(riskRating);
        customer.setPepStatus("NONE");
        customer.setOccupation(occupation);
        customer.setSourceOfFunds(sourceOfFunds);
        customer.setAccountOpened(ANALYSIS_TIME.toLocalDate()
                .minusDays(365));

        return customer;
    }

    private MockTransaction transaction(
            boolean flagged,
            String amount,
            String riskScore,
            String channel
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setFlagged(flagged);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setRiskScore(new BigDecimal(riskScore));
        transaction.setChannel(channel);
        transaction.setTransactionDate(ANALYSIS_TIME);
        transaction.setOriginCountry("United States");
        transaction.setDestinationCountry("United States");

        return transaction;
    }

    private void assertHas(
            FraudAnalysisResult result,
            FraudIndicatorType indicatorType
    ) {
        assertTrue(
                result.triggeredIndicators().stream()
                        .map(FraudIndicator::type)
                        .anyMatch(indicatorType::equals)
        );
    }

    private FraudIndicator indicator(
            FraudAnalysisResult result,
            FraudIndicatorType indicatorType
    ) {
        return result.triggeredIndicators().stream()
                .filter(item -> item.type() == indicatorType)
                .findFirst()
                .orElseThrow();
    }
}
