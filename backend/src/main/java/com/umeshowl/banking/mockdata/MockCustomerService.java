package com.umeshowl.banking.mockdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class MockCustomerService {

    private final MockCustomerRepository mockCustomerRepository;

    public MockCustomerService(
            MockCustomerRepository mockCustomerRepository
    ) {
        this.mockCustomerRepository = mockCustomerRepository;
    }

    @Transactional(readOnly = true)
    public List<MockCustomer> getAllCustomers() {
        return mockCustomerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MockCustomer getCustomer(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Customer ID is required"
            );
        }

        return mockCustomerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock customer not found: "
                                        + customerId
                        )
                );
    }

    @Transactional(readOnly = true)
    public MockCustomer getCustomerByAccountNumber(
            String accountNumber
    ) {
        String normalizedAccountNumber =
                requireNonBlank(
                        accountNumber,
                        "Account number is required"
                );

        return mockCustomerRepository
                .findByAccountNumber(normalizedAccountNumber)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock customer not found for account number: "
                                        + normalizedAccountNumber
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<MockCustomer> getCustomersByRiskRating(
            String riskRating
    ) {
        return mockCustomerRepository.findByRiskRating(
                requireNonBlank(
                        riskRating,
                        "Risk rating is required"
                )
        );
    }

    @Transactional(readOnly = true)
    public List<MockCustomer> getCustomersByKycStatus(
            String kycStatus
    ) {
        return mockCustomerRepository.findByKycStatus(
                requireNonBlank(
                        kycStatus,
                        "KYC status is required"
                )
        );
    }

    private String requireNonBlank(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }
}
