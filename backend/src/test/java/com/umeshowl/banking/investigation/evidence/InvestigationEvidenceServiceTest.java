package com.umeshowl.banking.investigation.evidence;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.knowledge.KnowledgeSearchService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import com.umeshowl.banking.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationEvidenceServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "90000000-0000-4000-8000-000000000001"
    );
    private static final UUID PROJECT_ID = UUID.fromString(
            "90000000-0000-4000-8000-000000000002"
    );
    private static final UUID FINDING_ID = UUID.fromString(
            "90000000-0000-4000-8000-000000000003"
    );

    private InvestigationCaseService investigationCaseService;
    private AgentFindingRepository agentFindingRepository;
    private KnowledgeSearchService knowledgeSearchService;
    private InvestigationEvidenceQueryBuilder queryBuilder;
    private AgentFindingCitationService citationService;
    private InvestigationEvidenceProperties properties;
    private InvestigationEvidenceService evidenceService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        knowledgeSearchService = mock(KnowledgeSearchService.class);
        queryBuilder = mock(InvestigationEvidenceQueryBuilder.class);
        citationService = mock(AgentFindingCitationService.class);
        properties = new InvestigationEvidenceProperties();
        properties.setMinimumRelevanceScore(new BigDecimal("0.500"));
        properties.setMaxCitationsPerAgent(2);
        properties.setSearchLimit(5);
        evidenceService = new InvestigationEvidenceService(
                investigationCaseService,
                agentFindingRepository,
                knowledgeSearchService,
                queryBuilder,
                citationService,
                properties
        );

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        Project project = new Project();
        project.setId(PROJECT_ID);
        investigationCase.setProject(project);
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
    }

    @Test
    void retrievesAndPersistsEvidenceForCompletedFinding() {
        AgentFinding finding = completedFinding("FRAUD");
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        KnowledgeSearchResult result = new KnowledgeSearchResult(
                chunkId,
                documentId,
                "fraud-policy.pdf",
                1,
                "Suspicious transaction monitoring policy",
                0.88d
        );

        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of(finding));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "KYC"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "AML"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "COMPLIANCE"
        )).thenReturn(List.of());
        when(queryBuilder.buildQuery(eq("FRAUD"), any(), eq(finding)))
                .thenReturn("fraud detection policy");
        when(queryBuilder.matchedReasonFor(eq("FRAUD"), any(), eq(finding)))
                .thenReturn("Matched FRAUD policy guidance");
        when(knowledgeSearchService.search(
                PROJECT_ID,
                "fraud detection policy",
                5,
                true
        )).thenReturn(List.of(result));
        AgentFindingCitation citation = new AgentFindingCitation();
        citation.setId(UUID.randomUUID());
        when(citationService.persistCitation(finding, result, "HYBRID"))
                .thenReturn(Optional.of(citation));

        InvestigationEvidenceResult evidenceResult =
                evidenceService.retrieveAndPersist(INVESTIGATION_ID);

        assertEquals(1, evidenceResult.totalCitationCount());
        assertEquals(1, evidenceResult.citationCountsByAgent().get("FRAUD"));
        assertEquals(
                "Matched FRAUD policy guidance",
                evidenceResult.evidenceByAgent()
                        .get("FRAUD")
                        .getFirst()
                        .matchedReason()
        );
        verify(citationService).persistCitation(finding, result, "HYBRID");
    }

    @Test
    void filtersLowScoreEvidenceAndPrefersDiverseDocuments() {
        UUID docA = UUID.randomUUID();
        UUID docB = UUID.randomUUID();
        List<KnowledgeSearchResult> ranked = List.of(
                new KnowledgeSearchResult(
                        UUID.randomUUID(), docA, "a.pdf", 0, "A1", 0.95d
                ),
                new KnowledgeSearchResult(
                        UUID.randomUUID(), docA, "a.pdf", 1, "A2", 0.90d
                ),
                new KnowledgeSearchResult(
                        UUID.randomUUID(), docB, "b.pdf", 0, "B1", 0.85d
                ),
                new KnowledgeSearchResult(
                        UUID.randomUUID(), UUID.randomUUID(), "c.pdf", 0, "C1", 0.20d
                )
        );

        List<KnowledgeSearchResult> selected =
                evidenceService.rankAndFilter(ranked);

        assertEquals(2, selected.size());
        assertEquals(docA, selected.get(0).documentId());
        assertEquals(docB, selected.get(1).documentId());
    }

    @Test
    void returnsWarningWhenEvidenceRetrievalFailsWithoutFailingFindings() {
        AgentFinding finding = completedFinding("AML");
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "KYC"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "AML"
        )).thenReturn(List.of(finding));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "COMPLIANCE"
        )).thenReturn(List.of());
        when(queryBuilder.buildQuery(eq("AML"), any(), eq(finding)))
                .thenReturn("AML escalation policy");
        when(queryBuilder.matchedReasonFor(eq("AML"), any(), eq(finding)))
                .thenReturn("Matched AML policy guidance");
        when(knowledgeSearchService.search(
                PROJECT_ID,
                "AML escalation policy",
                5,
                true
        )).thenThrow(new IllegalStateException("Embedding service unavailable"));

        InvestigationEvidenceResult evidenceResult =
                evidenceService.retrieveAndPersist(INVESTIGATION_ID);

        assertEquals(0, evidenceResult.totalCitationCount());
        assertTrue(evidenceResult.warnings().getFirst().contains("AML"));
        verify(citationService, never()).persistCitation(any(), any(), any());
    }

    private AgentFinding completedFinding(String agentType) {
        AgentFinding finding = new AgentFinding();
        finding.setId(FINDING_ID);
        finding.setAgentType(agentType);
        finding.setStatus("COMPLETE");
        finding.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        finding.setStructuredJson("""
                {"triggeredIndicators":[{"type":"FLAGGED_TRANSACTION"}]}
                """);
        return finding;
    }
}
