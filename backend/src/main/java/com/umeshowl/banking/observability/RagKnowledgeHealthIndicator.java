package com.umeshowl.banking.observability;

import com.umeshowl.banking.knowledge.DocumentChunkRepository;
import com.umeshowl.banking.knowledge.KnowledgeDocumentRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RagKnowledgeHealthIndicator implements HealthIndicator {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public RagKnowledgeHealthIndicator(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            DocumentChunkRepository documentChunkRepository
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Override
    public Health health() {
        try {
            long documentCount = knowledgeDocumentRepository.count();
            long chunkCount = documentChunkRepository.count();

            return Health.up()
                    .withDetail("documents", documentCount)
                    .withDetail("chunks", chunkCount)
                    .withDetail(
                            "status",
                            "Knowledge/RAG subsystem is reachable"
                    )
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail(
                            "status",
                            "Knowledge/RAG subsystem is unavailable"
                    )
                    .withException(exception)
                    .build();
        }
    }
}
