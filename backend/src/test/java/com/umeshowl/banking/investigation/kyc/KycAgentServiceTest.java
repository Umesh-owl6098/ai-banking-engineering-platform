package com.umeshowl.banking.investigation.kyc;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycAgentServiceTest {

    private static final UUID CASE_ID = UUID.fromString(
            "80000000-0000-4000-8000-000000000001"
    );
    private InvestigationCaseService caseService;
    private KycAgentProperties properties;
    private KycAgentService kycAgentService;

    @BeforeEach
    void setUp() {
        caseService = mock(InvestigationCaseService.class);
        properties = new KycAgentProperties();
        kycAgentService = new KycAgentService(
                caseService,
                properties
        );
    }

    @Test
    void triggersEveryCustomerKycIndicator() {
        MockCustomer customer = customer();
        customer.setKycStatus("PENDING");
        customer.setOccupation("UNKNOWN");
        customer.setSourceOfFunds(null);
        customer.setPepStatus("PEP");
        customer.setRiskRating("HIGH");
        customer.setNationality("Iran");
        customer.setCountryOfResidence("Syria");
        customer.setAccountStatus("FROZEN");
        customer.setAccountOpened(
                OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
        );
        MockTransaction transaction = transaction(true, "15000.00");

        KycAnalysisResult result = analyze(customer, transaction, false);

        for (KycIndicatorType type : KycIndicatorType.values()) {
            assertTrue(result.triggeredIndicators().stream()
                    .anyMatch(indicator -> indicator.type() == type));
        }
        assertEquals(KycRiskLevel.CRITICAL, result.riskLevel());
        assertEquals(100, result.totalScore());
    }

    @Test
    void acceptsVerifiedCompleteActiveLowRiskCustomer() {
        MockCustomer customer = customer();
        customer.setKycStatus("VERIFIED");
        customer.setOccupation("Engineer");
        customer.setSourceOfFunds("Salary");
        customer.setPepStatus("NONE");
        customer.setRiskRating("LOW");
        customer.setNationality("United States");
        customer.setCountryOfResidence("United States");
        customer.setAccountStatus("ACTIVE");
        customer.setAccountOpened(
                OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
                        .minusDays(365)
        );

        KycAnalysisResult result = analyze(customer, null, false);

        assertTrue(result.triggeredIndicators().isEmpty());
        assertEquals(KycRiskLevel.LOW, result.riskLevel());
    }

    @Test
    void derivesCustomerForTransactionOnlyCase() {
        MockCustomer customer = customer();
        customer.setKycStatus("PENDING");
        MockTransaction transaction = transaction(false, "100.00");
        transaction.setCustomer(customer);

        KycAnalysisResult result = analyze(customer, transaction, true);

        assertEquals(customer.getId(), result.customerId());
        assertTrue(result.triggeredIndicators().stream()
                .anyMatch(indicator ->
                        indicator.type()
                                == KycIndicatorType.KYC_NOT_VERIFIED));
    }

    @Test
    void assignsMediumHighAndClampedCriticalScores() {
        MockCustomer customer = customer();
        customer.setKycStatus("PENDING");
        customer.setOccupation("Engineer");
        customer.setSourceOfFunds("Salary");
        customer.setPepStatus("NONE");
        customer.setRiskRating("LOW");
        customer.setNationality("United States");
        customer.setCountryOfResidence("United States");
        customer.setAccountStatus("ACTIVE");
        customer.setAccountOpened(
                OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
                        .minusDays(365)
        );

        properties.setKycNotVerifiedScore(30);
        assertEquals(
                KycRiskLevel.MEDIUM,
                analyze(customer, null, false).riskLevel()
        );

        properties.setKycNotVerifiedScore(60);
        assertEquals(
                KycRiskLevel.HIGH,
                analyze(customer, null, false).riskLevel()
        );

        properties.setKycNotVerifiedScore(120);
        assertEquals(
                100,
                analyze(customer, null, false).totalScore()
        );
        assertEquals(
                KycRiskLevel.CRITICAL,
                analyze(customer, null, false).riskLevel()
        );
    }

    private KycAnalysisResult analyze(
            MockCustomer customer,
            MockTransaction transaction,
            boolean transactionOnly
    ) {
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(CASE_ID);
        investigationCase.setCustomer(
                transactionOnly ? null : customer
        );
        investigationCase.setTransaction(transaction);
        when(caseService.getCase(CASE_ID)).thenReturn(investigationCase);

        return kycAgentService.analyze(CASE_ID);
    }

    private MockCustomer customer() {
        MockCustomer customer = new MockCustomer();
        customer.setId(UUID.fromString(
                "80000000-0000-4000-8000-000000000002"
        ));
        return customer;
    }

    private MockTransaction transaction(
            boolean flagged,
            String amount
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setId(UUID.fromString(
                "80000000-0000-4000-8000-000000000003"
        ));
        transaction.setFlagged(flagged);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC));
        return transaction;
    }
}
