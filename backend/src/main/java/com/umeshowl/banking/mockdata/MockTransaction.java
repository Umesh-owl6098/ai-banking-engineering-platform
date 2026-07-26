package com.umeshowl.banking.mockdata;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "mock_transactions")
@Getter
@Setter
@NoArgsConstructor
public class MockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MockCustomer customer;

    @Column(name = "transaction_reference", nullable = false, length = 100, unique = true)
    private String transactionReference;

    @Column(name = "transaction_date", nullable = false)
    private OffsetDateTime transactionDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(name = "transaction_status", nullable = false, length = 50)
    private String transactionStatus;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "counterparty_name", length = 200)
    private String counterpartyName;

    @Column(name = "counterparty_bank", length = 200)
    private String counterpartyBank;

    @Column(name = "counterparty_country", length = 100)
    private String counterpartyCountry;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "destination_country", length = 100)
    private String destinationCountry;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "risk_indicators")
    private String[] riskIndicators;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "simulation_scenario", length = 50)
    private String simulationScenario;

    @Column(name = "scenario_group_id", length = 100)
    private String scenarioGroupId;

    @PrePersist
    public void beforeCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
