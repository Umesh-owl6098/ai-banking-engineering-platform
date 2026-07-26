package com.umeshowl.banking.investigation.dto;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AgentFindingResponse(
        UUID id,
        String agentType,
        String status,
        String riskLevel,
        BigDecimal confidence,
        String summary,
        String structuredJson,
        String ragQuery,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        List<AgentFindingCitationResponse> citations
) {
    public static AgentFindingResponse from(
            AgentFinding finding,
            List<AgentFindingCitation> citations
    ) {
        return new AgentFindingResponse(
                finding.getId(),
                finding.getAgentType(),
                finding.getStatus(),
                finding.getRiskLevel(),
                finding.getConfidence(),
                finding.getSummary(),
                finding.getStructuredJson(),
                finding.getRagQuery(),
                finding.getStartedAt(),
                finding.getCompletedAt(),
                finding.getCreatedAt(),
                citations.stream()
                        .map(AgentFindingCitationResponse::from)
                        .toList()
        );
    }

    public static AgentFindingResponse from(AgentFinding finding) {
        List<AgentFindingCitationResponse> citations =
                finding.getCitations() == null
                        ? List.of()
                        : finding.getCitations().stream()
                                .map(AgentFindingCitationResponse::from)
                                .toList();

        return new AgentFindingResponse(
                finding.getId(),
                finding.getAgentType(),
                finding.getStatus(),
                finding.getRiskLevel(),
                finding.getConfidence(),
                finding.getSummary(),
                finding.getStructuredJson(),
                finding.getRagQuery(),
                finding.getStartedAt(),
                finding.getCompletedAt(),
                finding.getCreatedAt(),
                citations
        );
    }
}
