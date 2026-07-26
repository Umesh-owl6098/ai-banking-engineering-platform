package com.umeshowl.banking.simulation.dto;

import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningStatus;
import com.umeshowl.banking.simulation.SimulationScenario;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record LiveTransactionEvent(
        UUID transactionId,
        String transactionReference,
        String customerName,
        BigDecimal amount,
        String currency,
        String channel,
        String transactionType,
        String originCountry,
        String destinationCountry,
        BigDecimal riskScore,
        boolean flagged,
        SimulationScenario scenario,
        String demoScenario,
        String scenarioGroupId,
        OffsetDateTime createdAt,
        TransactionScreeningStatus screeningStatus,
        String screeningReason,
        List<String> triggeredRules,
        OffsetDateTime screenedAt,
        UUID investigationId,
        String lifecycleStatus
) {
    public static LiveTransactionEvent from(
            MockTransaction transaction,
            SimulationScenario scenario,
            TransactionScreeningResult screeningResult
    ) {
        return from(
                transaction,
                scenario,
                screeningResult,
                null,
                transaction.getScenarioGroupId()
        );
    }

    public static LiveTransactionEvent from(
            MockTransaction transaction,
            SimulationScenario scenario,
            TransactionScreeningResult screeningResult,
            UUID investigationId
    ) {
        return from(
                transaction,
                scenario,
                screeningResult,
                investigationId,
                transaction.getScenarioGroupId()
        );
    }

    public static LiveTransactionEvent from(
            MockTransaction transaction,
            SimulationScenario scenario,
            TransactionScreeningResult screeningResult,
            UUID investigationId,
            String scenarioGroupId
    ) {
        MockCustomer customer = transaction.getCustomer();
        TransactionScreeningStatus screeningStatus = screeningResult == null
                ? TransactionScreeningStatus.PROCESSING
                : screeningResult.getStatus();
        String screeningReason = screeningResult == null
                ? "Screening in progress"
                : screeningResult.getReason();
        List<String> triggeredRules = screeningResult == null
                || screeningResult.getTriggeredRules() == null
                ? List.of()
                : List.copyOf(
                        Arrays.asList(screeningResult.getTriggeredRules())
                );
        OffsetDateTime screenedAt = screeningResult == null
                ? null
                : screeningResult.getScreenedAt();

        return new LiveTransactionEvent(
                transaction.getId(),
                transaction.getTransactionReference(),
                customer.getFullName(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getChannel(),
                transaction.getTransactionType(),
                transaction.getOriginCountry(),
                transaction.getDestinationCountry(),
                transaction.getRiskScore(),
                transaction.isFlagged(),
                scenario,
                transaction.getSimulationScenario(),
                scenarioGroupId,
                transaction.getCreatedAt(),
                screeningStatus,
                screeningReason,
                triggeredRules,
                screenedAt,
                investigationId,
                resolveLifecycleStatus(screeningStatus, investigationId)
        );
    }

    private static String resolveLifecycleStatus(
            TransactionScreeningStatus screeningStatus,
            UUID investigationId
    ) {
        if (investigationId != null) {
            return "INVESTIGATION_CREATED";
        }

        if (screeningStatus == TransactionScreeningStatus.PROCESSING) {
            return "PROCESSING";
        }

        return "SCREENED";
    }
}
