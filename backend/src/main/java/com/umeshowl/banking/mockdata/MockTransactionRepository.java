package com.umeshowl.banking.mockdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MockTransactionRepository
        extends JpaRepository<MockTransaction, UUID> {

    @Query("""
            select transaction
            from MockTransaction transaction
            join fetch transaction.customer
            where transaction.id = :transactionId
            """)
    Optional<MockTransaction> findByIdWithCustomer(
            @Param("transactionId") UUID transactionId
    );

    List<MockTransaction>
            findByCustomer_IdOrderByTransactionDateDesc(
                    UUID customerId
            );

    List<MockTransaction>
            findByFlaggedTrueOrderByTransactionDateDesc();

    List<MockTransaction>
            findByRiskScoreGreaterThanEqualOrderByRiskScoreDesc(
                    BigDecimal minimumRiskScore
            );

    Optional<MockTransaction> findByTransactionReference(
            String transactionReference
    );

    List<MockTransaction> findByScenarioGroupIdOrderByTransactionDateAsc(
            String scenarioGroupId
    );
}
