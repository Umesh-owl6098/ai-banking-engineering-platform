package com.umeshowl.banking.investigation.dto;

import com.umeshowl.banking.investigation.AgentFindingCitation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentFindingCitationResponse(
        UUID id,
        String fileName,
        Integer chunkIndex,
        BigDecimal similarity,
        String contentPreview,
        OffsetDateTime createdAt
) {
    public static AgentFindingCitationResponse from(
            AgentFindingCitation citation
    ) {
        return new AgentFindingCitationResponse(
                citation.getId(),
                citation.getFileName(),
                citation.getChunkIndex(),
                citation.getSimilarity(),
                citation.getContentPreview(),
                citation.getCreatedAt()
        );
    }
}
