package com.umeshowl.banking.screening;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionScreeningResultRepository
        extends JpaRepository<TransactionScreeningResult, UUID> {

    Optional<TransactionScreeningResult> findByTransaction_Id(
            UUID transactionId
    );

    long countByScreenedAtGreaterThanEqual(OffsetDateTime screenedAt);

    long countByStatusAndScreenedAtGreaterThanEqual(
            TransactionScreeningStatus status,
            OffsetDateTime screenedAt
    );

    @Query("""
            SELECT screeningResult
            FROM TransactionScreeningResult screeningResult
            JOIN FETCH screeningResult.transaction transaction
            JOIN FETCH transaction.customer customer
            ORDER BY screeningResult.screenedAt DESC
            """)
    List<TransactionScreeningResult> findRecentScreened(Pageable pageable);

    @Query("""
            SELECT screeningResult
            FROM TransactionScreeningResult screeningResult
            JOIN FETCH screeningResult.transaction transaction
            JOIN FETCH transaction.customer customer
            WHERE screeningResult.status IN :statuses
            ORDER BY screeningResult.screenedAt DESC
            """)
    List<TransactionScreeningResult> findRecentByStatuses(
            List<TransactionScreeningStatus> statuses,
            Pageable pageable
    );
}
