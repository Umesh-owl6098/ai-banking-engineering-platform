package com.umeshowl.banking.mockdata.dto;

import com.umeshowl.banking.mockdata.MockTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MockTransactionResponse(
        UUID id,
        UUID customerId,
        String transactionReference,
        OffsetDateTime transactionDate,
        BigDecimal amount,
        String currency,
        String transactionType,
        String transactionStatus,
        String channel,
        String counterpartyName,
        String counterpartyBank,
        String counterpartyCountry,
        String originCountry,
        String destinationCountry,
        String description,
        boolean flagged,
        BigDecimal riskScore,
        String[] riskIndicators,
        OffsetDateTime createdAt
) {

    public static MockTransactionResponse from(
            MockTransaction transaction
    ) {
        return new MockTransactionResponse(
                transaction.getId(),
                transaction.getCustomer().getId(),
                transaction.getTransactionReference(),
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTransactionType(),
                transaction.getTransactionStatus(),
                transaction.getChannel(),
                transaction.getCounterpartyName(),
                transaction.getCounterpartyBank(),
                transaction.getCounterpartyCountry(),
                transaction.getOriginCountry(),
                transaction.getDestinationCountry(),
                transaction.getDescription(),
                transaction.isFlagged(),
                transaction.getRiskScore(),
                transaction.getRiskIndicators(),
                transaction.getCreatedAt()
        );
    }
}
