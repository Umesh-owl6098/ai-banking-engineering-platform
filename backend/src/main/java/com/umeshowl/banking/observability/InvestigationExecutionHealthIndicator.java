package com.umeshowl.banking.observability;

import com.umeshowl.banking.investigation.aml.AmlAgentExecutionService;
import com.umeshowl.banking.investigation.compliance.ComplianceAgentExecutionService;
import com.umeshowl.banking.investigation.execution.InvestigationExecutionService;
import com.umeshowl.banking.investigation.fraud.FraudAgentExecutionService;
import com.umeshowl.banking.investigation.kyc.KycAgentExecutionService;
import com.umeshowl.banking.investigation.supervisor.SupervisorAgentService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class InvestigationExecutionHealthIndicator implements HealthIndicator {

    private final InvestigationExecutionService investigationExecutionService;
    private final SupervisorAgentService supervisorAgentService;
    private final FraudAgentExecutionService fraudAgentExecutionService;
    private final KycAgentExecutionService kycAgentExecutionService;
    private final AmlAgentExecutionService amlAgentExecutionService;
    private final ComplianceAgentExecutionService complianceAgentExecutionService;

    public InvestigationExecutionHealthIndicator(
            InvestigationExecutionService investigationExecutionService,
            SupervisorAgentService supervisorAgentService,
            FraudAgentExecutionService fraudAgentExecutionService,
            KycAgentExecutionService kycAgentExecutionService,
            AmlAgentExecutionService amlAgentExecutionService,
            ComplianceAgentExecutionService complianceAgentExecutionService
    ) {
        this.investigationExecutionService = investigationExecutionService;
        this.supervisorAgentService = supervisorAgentService;
        this.fraudAgentExecutionService = fraudAgentExecutionService;
        this.kycAgentExecutionService = kycAgentExecutionService;
        this.amlAgentExecutionService = amlAgentExecutionService;
        this.complianceAgentExecutionService = complianceAgentExecutionService;
    }

    @Override
    public Health health() {
        boolean ready = investigationExecutionService != null
                && supervisorAgentService != null
                && fraudAgentExecutionService != null
                && kycAgentExecutionService != null
                && amlAgentExecutionService != null
                && complianceAgentExecutionService != null;

        if (!ready) {
            return Health.down()
                    .withDetail(
                            "status",
                            "Investigation execution subsystem is not ready"
                    )
                    .build();
        }

        return Health.up()
                .withDetail("supervisor", "available")
                .withDetail("fraudAgent", "available")
                .withDetail("kycAgent", "available")
                .withDetail("amlAgent", "available")
                .withDetail("complianceAgent", "available")
                .withDetail(
                        "status",
                        "Investigation execution subsystem is ready"
                )
                .build();
    }
}
