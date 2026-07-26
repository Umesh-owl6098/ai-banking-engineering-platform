package com.umeshowl.banking.knowledge.dto;

import java.util.UUID;

public record KnowledgeSearchResult(
        UUID chunkId,
        UUID documentId,
        String fileName,
        int chunkIndex,
        String content,
        double similarity
) {
}