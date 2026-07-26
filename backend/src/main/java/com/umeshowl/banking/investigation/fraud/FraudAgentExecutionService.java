package com.umeshowl.banking.investigation.fraud;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FraudAgentExecutionService {

    private final FraudAgentService fraudAgentService;
    private final AgentFindingService agentFindingService;

    public FraudAgentExecutionService(
            FraudAgentService fraudAgentService,
            AgentFindingService agentFindingService
    ) {
        this.fraudAgentService = fraudAgentService;
        this.agentFindingService = agentFindingService;
    }

    @Transactional
    public AgentFinding execute(UUID investigationId) {
        FraudAnalysisResult analysis =
                fraudAgentService.analyze(investigationId);

        return agentFindingService.persistFraudAnalysis(analysis);
    }
}
