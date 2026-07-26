package com.umeshowl.banking.mockdata;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mock_customers")
@Getter
@Setter
@NoArgsConstructor
public class MockCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 100)
    private String nationality;

    @Column(name = "country_of_residence", nullable = false, length = 100)
    private String countryOfResidence;

    @Column(name = "account_number", nullable = false, length = 50, unique = true)
    private String accountNumber;

    @Column(name = "account_status", nullable = false, length = 50)
    private String accountStatus;

    @Column(length = 255)
    private String email;

    @Column(length = 150)
    private String occupation;

    @Column(name = "source_of_funds", length = 200)
    private String sourceOfFunds;

    @Column(name = "kyc_status", nullable = false, length = 50)
    private String kycStatus;

    @Column(name = "risk_rating", nullable = false, length = 20)
    private String riskRating;

    @Column(name = "pep_status", nullable = false, length = 20)
    private String pepStatus;

    @Column(name = "account_opened", nullable = false)
    private LocalDate accountOpened;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<MockTransaction> transactions = new ArrayList<>();

    @PrePersist
    public void beforeCreate() {
        if (pepStatus == null) {
            pepStatus = "NONE";
        }

        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
