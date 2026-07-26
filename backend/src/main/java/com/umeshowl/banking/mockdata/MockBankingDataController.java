package com.umeshowl.banking.mockdata;

import com.umeshowl.banking.mockdata.dto.MockCustomerResponse;
import com.umeshowl.banking.mockdata.dto.MockTransactionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mock")
public class MockBankingDataController {

    private final MockCustomerService mockCustomerService;
    private final MockTransactionService mockTransactionService;

    public MockBankingDataController(
            MockCustomerService mockCustomerService,
            MockTransactionService mockTransactionService
    ) {
        this.mockCustomerService = mockCustomerService;
        this.mockTransactionService = mockTransactionService;
    }

    @GetMapping("/customers")
    public List<MockCustomerResponse> getCustomers(
            @RequestParam(required = false) String riskRating,
            @RequestParam(required = false) String kycStatus
    ) {
        if (hasText(riskRating) && hasText(kycStatus)) {
            throw new IllegalArgumentException(
                    "Only one customer filter may be provided"
            );
        }

        if (riskRating != null) {
            return mockCustomerService
                    .getCustomersByRiskRating(riskRating)
                    .stream()
                    .map(MockCustomerResponse::from)
                    .toList();
        }

        if (kycStatus != null) {
            return mockCustomerService
                    .getCustomersByKycStatus(kycStatus)
                    .stream()
                    .map(MockCustomerResponse::from)
                    .toList();
        }

        return mockCustomerService.getAllCustomers()
                .stream()
                .map(MockCustomerResponse::from)
                .toList();
    }

    @GetMapping("/customers/account/{accountNumber}")
    public MockCustomerResponse getCustomerByAccountNumber(
            @PathVariable String accountNumber
    ) {
        return MockCustomerResponse.from(
                mockCustomerService.getCustomerByAccountNumber(
                        accountNumber
                )
        );
    }

    @GetMapping("/customers/{id}")
    public MockCustomerResponse getCustomer(
            @PathVariable UUID id
    ) {
        return MockCustomerResponse.from(
                mockCustomerService.getCustomer(id)
        );
    }

    @GetMapping("/customers/{customerId}/transactions")
    public List<MockTransactionResponse> getCustomerTransactions(
            @PathVariable UUID customerId
    ) {
        return mockTransactionService
                .getTransactionsForCustomer(customerId)
                .stream()
                .map(MockTransactionResponse::from)
                .toList();
    }

    @GetMapping("/transactions")
    public List<MockTransactionResponse> getTransactions(
            @RequestParam(required = false)
            BigDecimal minimumRiskScore
    ) {
        if (minimumRiskScore == null) {
            throw new IllegalArgumentException(
                    "minimumRiskScore is required"
            );
        }

        return mockTransactionService
                .getTransactionsAboveRiskScore(
                        minimumRiskScore
                )
                .stream()
                .map(MockTransactionResponse::from)
                .toList();
    }

    @GetMapping("/transactions/flagged")
    public List<MockTransactionResponse> getFlaggedTransactions() {
        return mockTransactionService.getFlaggedTransactions()
                .stream()
                .map(MockTransactionResponse::from)
                .toList();
    }

    @GetMapping("/transactions/reference/{reference}")
    public MockTransactionResponse getTransactionByReference(
            @PathVariable String reference
    ) {
        return MockTransactionResponse.from(
                mockTransactionService.getTransactionByReference(
                        reference
                )
        );
    }

    @GetMapping("/transactions/{id}")
    public MockTransactionResponse getTransaction(
            @PathVariable UUID id
    ) {
        return MockTransactionResponse.from(
                mockTransactionService.getTransaction(id)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
