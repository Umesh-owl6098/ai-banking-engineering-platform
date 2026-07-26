package com.umeshowl.banking.investigation.supervisor;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorAgentServiceTest {

    private static final UUID INVESTIGATION_ID =
            UUID.fromString(
                    "30000000-0000-4000-8000-000000000001"
            );

    private InvestigationCaseService investigationCaseService;

    private SupervisorAgentService supervisorAgentService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(
                InvestigationCaseService.class
        );

        SupervisorAgentProperties properties =
                new SupervisorAgentProperties();
        properties.setAmountThreshold(
                new BigDecimal("10000.00")
        );
        properties.setRiskScoreThreshold(
                new BigDecimal("75.00")
        );

        supervisorAgentService = new SupervisorAgentService(
                investigationCaseService,
                properties
        );
    }

    @Test
    void plansFraudKycAndComplianceWithoutAmlForLowRiskCase() {
        InvestigationCase investigationCase =
                investigationCase(
                        customer("LOW", "NONE"),
                        transaction(false, "9999.99", "75.00")
                );
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);

        InvestigationExecutionPlan plan =
                supervisorAgentService.planInvestigation(
                        INVESTIGATION_ID
                );

        assertEquals(
                List.of(
                        AgentType.FRAUD,
                        AgentType.KYC,
                        AgentType.COMPLIANCE
                ),
                plan.steps().stream()
                        .map(AgentExecutionStep::agentType)
                        .toList()
        );
        assertEquals(
                ExecutionPlanStatus.PLANNED,
                plan.status()
        );
        verify(investigationCaseService).getCase(INVESTIGATION_ID);
    }

    @Test
    void includesAmlForFlaggedTransaction() {
        InvestigationExecutionPlan plan = planFor(
                customer("LOW", "NONE"),
                transaction(true, "100.00", "1.00")
        );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(amlReason(plan).contains("flagged"));
    }

    @Test
    void includesAmlWhenAmountExceedsThreshold() {
        InvestigationExecutionPlan plan = planFor(
                customer("LOW", "NONE"),
                transaction(false, "10000.01", "1.00")
        );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(amlReason(plan).contains("amount exceeds"));
    }

    @Test
    void includesAmlWhenRiskScoreExceedsThreshold() {
        InvestigationExecutionPlan plan = planFor(
                customer("LOW", "NONE"),
                transaction(false, "100.00", "75.01")
        );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(amlReason(plan).contains("risk score exceeds"));
    }

    @Test
    void includesAmlForHighRiskCustomer() {
        InvestigationExecutionPlan plan = planFor(
                customer("HIGH", "NONE"),
                transaction(false, "100.00", "1.00")
        );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(
                amlReason(plan).contains("HIGH risk rating")
        );
    }

    @Test
    void includesAmlForPepCustomer() {
        InvestigationExecutionPlan plan = planFor(
                customer("LOW", "PEP"),
                transaction(false, "100.00", "1.00")
        );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(
                amlReason(plan).contains("politically exposed person")
        );
    }

    @Test
    void usesTransactionCustomerWhenCaseCustomerIsAbsent() {
        MockCustomer transactionCustomer =
                customer("HIGH", "NONE");
        MockTransaction transaction = transaction(
                false,
                "100.00",
                "1.00"
        );
        transaction.setCustomer(transactionCustomer);
        InvestigationCase investigationCase =
                investigationCase(null, transaction);
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);

        InvestigationExecutionPlan plan =
                supervisorAgentService.planInvestigation(
                        INVESTIGATION_ID
                );

        assertAmlIsBeforeCompliance(plan);
        assertTrue(
                amlReason(plan).contains("HIGH risk rating")
        );
    }

    private InvestigationExecutionPlan planFor(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase(customer, transaction));

        return supervisorAgentService.planInvestigation(
                INVESTIGATION_ID
        );
    }

    private InvestigationCase investigationCase(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        InvestigationCase investigationCase =
                new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setCustomer(customer);
        investigationCase.setTransaction(transaction);

        return investigationCase;
    }

    private MockCustomer customer(
            String riskRating,
            String pepStatus
    ) {
        MockCustomer customer = new MockCustomer();
        customer.setRiskRating(riskRating);
        customer.setPepStatus(pepStatus);

        return customer;
    }

    private MockTransaction transaction(
            boolean flagged,
            String amount,
            String riskScore
    ) {
        MockTransaction transaction = new MockTransaction();
        transaction.setFlagged(flagged);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setRiskScore(new BigDecimal(riskScore));

        return transaction;
    }

    private void assertAmlIsBeforeCompliance(
            InvestigationExecutionPlan plan
    ) {
        assertEquals(
                List.of(
                        AgentType.FRAUD,
                        AgentType.KYC,
                        AgentType.AML,
                        AgentType.COMPLIANCE
                ),
                plan.steps().stream()
                        .map(AgentExecutionStep::agentType)
                        .toList()
        );
    }

    private String amlReason(InvestigationExecutionPlan plan) {
        return plan.steps().stream()
                .filter(step ->
                        step.agentType() == AgentType.AML
                )
                .findFirst()
                .orElseThrow()
                .selectionReason();
    }
}
