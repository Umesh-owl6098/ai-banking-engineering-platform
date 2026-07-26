package com.umeshowl.banking.investigation.compliance;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class ComplianceAgentExecutionService {
    private final ComplianceAgentService agent; private final AgentFindingService findings;
    public ComplianceAgentExecutionService(ComplianceAgentService agent,AgentFindingService findings){this.agent=agent;this.findings=findings;}
    @Transactional public AgentFinding execute(UUID investigationId){return findings.persistComplianceAnalysis(agent.analyze(investigationId));}
}
