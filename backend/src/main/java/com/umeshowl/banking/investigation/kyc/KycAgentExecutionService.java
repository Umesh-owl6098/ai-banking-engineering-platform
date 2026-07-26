package com.umeshowl.banking.investigation.kyc;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class KycAgentExecutionService {

    private final KycAgentService kycAgentService;
    private final AgentFindingService agentFindingService;

    public KycAgentExecutionService(
            KycAgentService kycAgentService,
            AgentFindingService agentFindingService
    ) {
        this.kycAgentService = kycAgentService;
        this.agentFindingService = agentFindingService;
    }

    @Transactional
    public AgentFinding execute(UUID investigationId) {
        KycAnalysisResult analysis =
                kycAgentService.analyze(investigationId);

        return agentFindingService.persistKycAnalysis(analysis);
    }
}
