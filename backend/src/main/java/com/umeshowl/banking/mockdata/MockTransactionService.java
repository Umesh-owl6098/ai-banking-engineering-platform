package com.umeshowl.banking.mockdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class MockTransactionService {

    private final MockTransactionRepository mockTransactionRepository;

    public MockTransactionService(
            MockTransactionRepository mockTransactionRepository
    ) {
        this.mockTransactionRepository =
                mockTransactionRepository;
    }

    @Transactional(readOnly = true)
    public List<MockTransaction> getTransactionsForCustomer(
            UUID customerId
    ) {
        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Customer ID is required"
            );
        }

        return mockTransactionRepository
                .findByCustomer_IdOrderByTransactionDateDesc(
                        customerId
                );
    }

    @Transactional(readOnly = true)
    public MockTransaction getTransaction(
            UUID transactionId
    ) {
        if (transactionId == null) {
            throw new IllegalArgumentException(
                    "Transaction ID is required"
            );
        }

        return mockTransactionRepository
                .findById(transactionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock transaction not found: "
                                        + transactionId
                        )
                );
    }

    @Transactional(readOnly = true)
    public MockTransaction getTransactionByReference(
            String transactionReference
    ) {
        if (transactionReference == null
                || transactionReference.isBlank()) {

            throw new IllegalArgumentException(
                    "Transaction reference is required"
            );
        }

        String normalizedReference =
                transactionReference.trim();

        return mockTransactionRepository
                .findByTransactionReference(
                        normalizedReference
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock transaction not found for reference: "
                                        + normalizedReference
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<MockTransaction> getFlaggedTransactions() {
        return mockTransactionRepository
                .findByFlaggedTrueOrderByTransactionDateDesc();
    }

    @Transactional(readOnly = true)
    public List<MockTransaction> getTransactionsAboveRiskScore(
            BigDecimal minimumRiskScore
    ) {
        if (minimumRiskScore == null) {
            throw new IllegalArgumentException(
                    "Minimum risk score is required"
            );
        }

        if (minimumRiskScore.compareTo(BigDecimal.ZERO) < 0
                || minimumRiskScore.compareTo(
                        BigDecimal.valueOf(100)
                ) > 0) {

            throw new IllegalArgumentException(
                    "Minimum risk score must be between 0 and 100"
            );
        }

        return mockTransactionRepository
                .findByRiskScoreGreaterThanEqualOrderByRiskScoreDesc(
                        minimumRiskScore
                );
    }
}
