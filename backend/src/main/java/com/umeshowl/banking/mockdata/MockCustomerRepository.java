package com.umeshowl.banking.mockdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MockCustomerRepository
        extends JpaRepository<MockCustomer, UUID> {

    Optional<MockCustomer> findByAccountNumber(
            String accountNumber
    );

    List<MockCustomer> findByRiskRating(
            String riskRating
    );

    List<MockCustomer> findByKycStatus(
            String kycStatus
    );

    List<MockCustomer> findByAccountStatus(String accountStatus);

    List<MockCustomer> findByPepStatusNot(String pepStatus);
}
