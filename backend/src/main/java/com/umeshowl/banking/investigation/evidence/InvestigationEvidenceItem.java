package com.umeshowl.banking.investigation.evidence;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record InvestigationEvidenceItem(
        String agentType,
        UUID documentId,
        String documentName,
        UUID chunkId,
        String chunkText,
        double relevanceScore,
        String retrievalMethod,
        Map<String, Object> metadata,
        String matchedReason
) {
    public InvestigationEvidenceItem {
        Objects.requireNonNull(agentType, "Agent type is required");
        Objects.requireNonNull(documentId, "Document ID is required");
        Objects.requireNonNull(documentName, "Document name is required");
        Objects.requireNonNull(chunkId, "Chunk ID is required");
        Objects.requireNonNull(retrievalMethod, "Retrieval method is required");
        Objects.requireNonNull(matchedReason, "Matched reason is required");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
