package com.umeshowl.banking.mockdata.dto;

import com.umeshowl.banking.mockdata.MockCustomer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MockCustomerResponse(
        UUID id,
        String fullName,
        LocalDate dateOfBirth,
        String nationality,
        String countryOfResidence,
        String accountNumber,
        String accountStatus,
        String email,
        String occupation,
        String sourceOfFunds,
        String kycStatus,
        String riskRating,
        String pepStatus,
        LocalDate accountOpened,
        OffsetDateTime createdAt
) {

    public static MockCustomerResponse from(
            MockCustomer customer
    ) {
        return new MockCustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getDateOfBirth(),
                customer.getNationality(),
                customer.getCountryOfResidence(),
                customer.getAccountNumber(),
                customer.getAccountStatus(),
                customer.getEmail(),
                customer.getOccupation(),
                customer.getSourceOfFunds(),
                customer.getKycStatus(),
                customer.getRiskRating(),
                customer.getPepStatus(),
                customer.getAccountOpened(),
                customer.getCreatedAt()
        );
    }
}
