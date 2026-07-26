package com.umeshowl.banking.screening;

import com.umeshowl.banking.mockdata.MockTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "transaction_screening_results")
@Getter
@Setter
@NoArgsConstructor
public class TransactionScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private MockTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionScreeningStatus status;

    @Column(name = "screening_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal screeningScore = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "triggered_rules")
    private String[] triggeredRules = new String[0];

    @Column(length = 500)
    private String reason;

    @Column(name = "screened_at")
    private OffsetDateTime screenedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void beforeCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
