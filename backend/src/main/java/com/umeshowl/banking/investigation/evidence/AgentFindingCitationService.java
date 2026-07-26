package com.umeshowl.banking.investigation.evidence;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.knowledge.DocumentChunk;
import com.umeshowl.banking.knowledge.DocumentChunkRepository;
import com.umeshowl.banking.knowledge.KnowledgeDocument;
import com.umeshowl.banking.knowledge.KnowledgeDocumentRepository;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentFindingCitationService {

    private static final int PREVIEW_LENGTH = 250;

    private final AgentFindingCitationRepository citationRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public AgentFindingCitationService(
            AgentFindingCitationRepository citationRepository,
            DocumentChunkRepository documentChunkRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository
    ) {
        this.citationRepository = citationRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Transactional
    public Optional<AgentFindingCitation> persistCitation(
            AgentFinding finding,
            KnowledgeSearchResult searchResult,
            String retrievalMethod
    ) {
        if (finding == null || finding.getId() == null) {
            throw new InvestigationEvidenceIntegrityException(
                    "Cannot persist citation without a saved finding"
            );
        }

        if (citationRepository.existsByFinding_IdAndChunk_Id(
                finding.getId(),
                searchResult.chunkId()
        )) {
            return Optional.empty();
        }

        DocumentChunk chunk = documentChunkRepository
                .findById(searchResult.chunkId())
                .orElseThrow(() ->
                        new InvestigationEvidenceIntegrityException(
                                "Document chunk not found: "
                                        + searchResult.chunkId()
                        )
                );

        KnowledgeDocument chunkDocument = chunk.getDocument();
        if (chunkDocument == null
                || chunkDocument.getId() == null
                || !chunkDocument.getId().equals(searchResult.documentId())) {
            throw new InvestigationEvidenceIntegrityException(
                    "Chunk "
                            + searchResult.chunkId()
                            + " does not belong to document "
                            + searchResult.documentId()
            );
        }

        KnowledgeDocument document = knowledgeDocumentRepository
                .findById(searchResult.documentId())
                .orElseThrow(() ->
                        new InvestigationEvidenceIntegrityException(
                                "Knowledge document not found: "
                                        + searchResult.documentId()
                        )
                );

        AgentFindingCitation citation = new AgentFindingCitation();
        citation.setFinding(finding);
        citation.setChunk(chunk);
        citation.setDocument(document);
        citation.setFileName(searchResult.fileName());
        citation.setChunkIndex(searchResult.chunkIndex());
        citation.setSimilarity(normalizeSimilarity(searchResult.similarity()));
        citation.setContentPreview(createPreview(searchResult.content()));

        return Optional.of(citationRepository.save(citation));
    }

    BigDecimal normalizeSimilarity(double similarity) {
        double clamped = Math.max(0.0d, Math.min(1.0d, similarity));
        return BigDecimal.valueOf(clamped).setScale(5, RoundingMode.HALF_UP);
    }

    String createPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, PREVIEW_LENGTH) + "...";
    }
}
