package com.umeshowl.banking.investigation.supervisor;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SupervisorAgentService {

    private final InvestigationCaseService
            investigationCaseService;

    private final SupervisorAgentProperties properties;

    public SupervisorAgentService(
            InvestigationCaseService investigationCaseService,
            SupervisorAgentProperties properties
    ) {
        this.investigationCaseService =
                investigationCaseService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public InvestigationExecutionPlan planInvestigation(
            UUID investigationId
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(
                        investigationId
                );
        MockCustomer customer = investigationCase.getCustomer();
        MockTransaction transaction =
                investigationCase.getTransaction();

        if (customer == null && transaction != null) {
            customer = transaction.getCustomer();
        }

        List<AgentExecutionStep> steps = new ArrayList<>();

        steps.add(new AgentExecutionStep(
                AgentType.FRAUD,
                "Fraud analysis is required for every investigation"
        ));
        steps.add(new AgentExecutionStep(
                AgentType.KYC,
                "KYC analysis is required for every investigation"
        ));

        List<String> amlReasons = getAmlSelectionReasons(
                customer,
                transaction
        );

        if (!amlReasons.isEmpty()) {
            steps.add(new AgentExecutionStep(
                    AgentType.AML,
                    String.join("; ", amlReasons)
            ));
        }

        steps.add(new AgentExecutionStep(
                AgentType.COMPLIANCE,
                "Compliance analysis runs after specialist analysis"
        ));

        return new InvestigationExecutionPlan(
                investigationCase.getId(),
                steps,
                ExecutionPlanStatus.PLANNED,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<String> getAmlSelectionReasons(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        List<String> reasons = new ArrayList<>();

        if (transaction != null) {
            if (transaction.isFlagged()) {
                reasons.add("Transaction is flagged");
            }

            if (isGreaterThan(
                    transaction.getAmount(),
                    properties.getAmountThreshold()
            )) {
                reasons.add(
                        "Transaction amount exceeds the AML threshold"
                );
            }

            if (isGreaterThan(
                    transaction.getRiskScore(),
                    properties.getRiskScoreThreshold()
            )) {
                reasons.add(
                        "Transaction risk score exceeds the AML threshold"
                );
            }
        }

        if (customer != null
                && "HIGH".equalsIgnoreCase(
                        customer.getRiskRating()
                )) {
            reasons.add("Customer has a HIGH risk rating");
        }

        if (customer != null
                && "PEP".equalsIgnoreCase(
                        customer.getPepStatus()
                )) {
            reasons.add("Customer is a politically exposed person");
        }

        return reasons;
    }

    private boolean isGreaterThan(
            BigDecimal value,
            BigDecimal threshold
    ) {
        return value != null
                && threshold != null
                && value.compareTo(threshold) > 0;
    }
}
