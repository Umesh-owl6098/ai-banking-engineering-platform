package com.umeshowl.banking.investigation.fraud;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FraudAgentExecutionServiceTest {

    @Test
    void analyzesThenPersistsFraudResult() {
        UUID investigationId = UUID.fromString(
                "60000000-0000-4000-8000-000000000001"
        );
        FraudAgentService fraudAgentService = mock(
                FraudAgentService.class
        );
        AgentFindingService agentFindingService = mock(
                AgentFindingService.class
        );
        FraudAgentExecutionService executionService =
                new FraudAgentExecutionService(
                        fraudAgentService,
                        agentFindingService
                );
        FraudAnalysisResult analysis = new FraudAnalysisResult(
                investigationId,
                null,
                null,
                0,
                FraudRiskLevel.LOW,
                "No indicators",
                List.of(),
                OffsetDateTime.of(
                        2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC
                )
        );
        AgentFinding finding = new AgentFinding();

        when(fraudAgentService.analyze(investigationId))
                .thenReturn(analysis);
        when(agentFindingService.persistFraudAnalysis(analysis))
                .thenReturn(finding);

        AgentFinding result = executionService.execute(investigationId);

        assertSame(finding, result);
        verify(fraudAgentService).analyze(investigationId);
        verify(agentFindingService).persistFraudAnalysis(analysis);
    }
}
