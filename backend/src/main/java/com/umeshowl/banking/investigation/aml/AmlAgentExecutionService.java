package com.umeshowl.banking.investigation.aml;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AmlAgentExecutionService {
    private final AmlAgentService amlAgentService;
    private final AgentFindingService findingService;
    public AmlAgentExecutionService(AmlAgentService amlAgentService, AgentFindingService findingService) {
        this.amlAgentService=amlAgentService; this.findingService=findingService;
    }
    @Transactional
    public AgentFinding execute(UUID investigationId) {
        return findingService.persistAmlAnalysis(
                amlAgentService.analyze(investigationId)
        );
    }
}
