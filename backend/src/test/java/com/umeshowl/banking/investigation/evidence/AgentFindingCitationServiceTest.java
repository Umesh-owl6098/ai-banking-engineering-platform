package com.umeshowl.banking.investigation.evidence;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.knowledge.DocumentChunk;
import com.umeshowl.banking.knowledge.DocumentChunkRepository;
import com.umeshowl.banking.knowledge.KnowledgeDocument;
import com.umeshowl.banking.knowledge.KnowledgeDocumentRepository;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentFindingCitationServiceTest {

    private static final UUID FINDING_ID = UUID.fromString(
            "80000000-0000-4000-8000-000000000001"
    );
    private static final UUID DOCUMENT_ID = UUID.fromString(
            "80000000-0000-4000-8000-000000000002"
    );
    private static final UUID CHUNK_ID = UUID.fromString(
            "80000000-0000-4000-8000-000000000003"
    );

    private AgentFindingCitationRepository citationRepository;
    private DocumentChunkRepository documentChunkRepository;
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    private AgentFindingCitationService citationService;

    @BeforeEach
    void setUp() {
        citationRepository = mock(AgentFindingCitationRepository.class);
        documentChunkRepository = mock(DocumentChunkRepository.class);
        knowledgeDocumentRepository = mock(KnowledgeDocumentRepository.class);
        citationService = new AgentFindingCitationService(
                citationRepository,
                documentChunkRepository,
                knowledgeDocumentRepository
        );
    }

    @Test
    void persistsCitationForValidDocumentAndChunkRelationship() {
        AgentFinding finding = finding();
        KnowledgeSearchResult searchResult = searchResult(0.82d);
        KnowledgeDocument document = document();
        DocumentChunk chunk = chunk(document);

        when(citationRepository.existsByFinding_IdAndChunk_Id(
                FINDING_ID,
                CHUNK_ID
        )).thenReturn(false);
        when(documentChunkRepository.findById(CHUNK_ID))
                .thenReturn(Optional.of(chunk));
        when(knowledgeDocumentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        when(citationRepository.save(any(AgentFindingCitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<AgentFindingCitation> saved = citationService.persistCitation(
                finding,
                searchResult,
                "HYBRID"
        );

        assertTrue(saved.isPresent());
        assertEquals(DOCUMENT_ID, saved.get().getDocument().getId());
        assertEquals(CHUNK_ID, saved.get().getChunk().getId());
        assertEquals(new BigDecimal("0.82000"), saved.get().getSimilarity());
        assertTrue(saved.get().getContentPreview().contains("policy excerpt"));
    }

    @Test
    void skipsDuplicateCitationForSameFindingAndChunk() {
        AgentFinding finding = finding();
        when(citationRepository.existsByFinding_IdAndChunk_Id(
                FINDING_ID,
                CHUNK_ID
        )).thenReturn(true);

        Optional<AgentFindingCitation> saved = citationService.persistCitation(
                finding,
                searchResult(0.90d),
                "HYBRID"
        );

        assertTrue(saved.isEmpty());
        verify(citationRepository, never()).save(any());
    }

    @Test
    void rejectsCitationWhenChunkDoesNotBelongToDocument() {
        AgentFinding finding = finding();
        KnowledgeDocument otherDocument = document();
        otherDocument.setId(UUID.randomUUID());
        DocumentChunk chunk = chunk(otherDocument);

        when(citationRepository.existsByFinding_IdAndChunk_Id(
                FINDING_ID,
                CHUNK_ID
        )).thenReturn(false);
        when(documentChunkRepository.findById(CHUNK_ID))
                .thenReturn(Optional.of(chunk));

        assertThrows(
                InvestigationEvidenceIntegrityException.class,
                () -> citationService.persistCitation(
                        finding,
                        searchResult(0.75d),
                        "VECTOR"
                )
        );
    }

    @Test
    void clampsKeywordSimilarityAboveOneBeforePersistence() {
        assertEquals(
                new BigDecimal("1.00000"),
                citationService.normalizeSimilarity(1.42d)
        );
    }

    private AgentFinding finding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(FINDING_ID);
        return finding;
    }

    private KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(DOCUMENT_ID);
        return document;
    }

    private DocumentChunk chunk(KnowledgeDocument document) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(CHUNK_ID);
        chunk.setDocument(document);
        chunk.setChunkIndex(2);
        return chunk;
    }

    private KnowledgeSearchResult searchResult(double similarity) {
        return new KnowledgeSearchResult(
                CHUNK_ID,
                DOCUMENT_ID,
                "fraud-policy.pdf",
                2,
                "This policy excerpt defines suspicious transaction escalation.",
                similarity
        );
    }
}
