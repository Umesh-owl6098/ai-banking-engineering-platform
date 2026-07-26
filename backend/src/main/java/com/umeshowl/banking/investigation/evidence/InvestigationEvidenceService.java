package com.umeshowl.banking.investigation.evidence;

import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.knowledge.KnowledgeSearchService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InvestigationEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationEvidenceService.class
    );

    private static final List<String> AGENT_TYPES = List.of(
            "FRAUD",
            "KYC",
            "AML",
            "COMPLIANCE"
    );

    private final InvestigationCaseService investigationCaseService;
    private final AgentFindingRepository agentFindingRepository;
    private final KnowledgeSearchService knowledgeSearchService;
    private final InvestigationEvidenceQueryBuilder queryBuilder;
    private final AgentFindingCitationService citationService;
    private final InvestigationEvidenceProperties properties;

    public InvestigationEvidenceService(
            InvestigationCaseService investigationCaseService,
            AgentFindingRepository agentFindingRepository,
            KnowledgeSearchService knowledgeSearchService,
            InvestigationEvidenceQueryBuilder queryBuilder,
            AgentFindingCitationService citationService,
            InvestigationEvidenceProperties properties
    ) {
        this.investigationCaseService = investigationCaseService;
        this.agentFindingRepository = agentFindingRepository;
        this.knowledgeSearchService = knowledgeSearchService;
        this.queryBuilder = queryBuilder;
        this.citationService = citationService;
        this.properties = properties;
    }

    @Transactional
    public InvestigationEvidenceResult retrieveAndPersist(
            UUID investigationId
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        UUID projectId = investigationCase.getProject().getId();

        Map<String, List<InvestigationEvidenceItem>> evidenceByAgent =
                new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (String agentType : AGENT_TYPES) {
            AgentFinding finding = latestCompletedFinding(
                    investigationId,
                    agentType
            );
            if (finding == null) {
                continue;
            }

            try {
                evidenceByAgent.put(
                        agentType,
                        retrieveForFinding(
                                investigationCase,
                                projectId,
                                finding,
                                agentType
                        )
                );
            } catch (InvestigationEvidenceIntegrityException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                String warning = "Evidence retrieval failed for "
                        + agentType
                        + ": "
                        + exception.getMessage();
                warnings.add(warning);
                log.warn(
                        "investigation_evidence_warning investigationId={} agentType={} message={}",
                        investigationId,
                        agentType,
                        exception.getMessage(),
                        exception
                );
                evidenceByAgent.put(agentType, List.of());
            }
        }

        Map<String, Integer> citationCounts =
                InvestigationEvidenceResult.countsFrom(evidenceByAgent);
        int totalCitationCount = citationCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        return new InvestigationEvidenceResult(
                investigationId,
                evidenceByAgent,
                citationCounts,
                totalCitationCount,
                warnings
        );
    }

    private List<InvestigationEvidenceItem> retrieveForFinding(
            InvestigationCase investigationCase,
            UUID projectId,
            AgentFinding finding,
            String agentType
    ) {
        String query = queryBuilder.buildQuery(
                agentType,
                investigationCase,
                finding
        );
        String matchedReason = queryBuilder.matchedReasonFor(
                agentType,
                investigationCase,
                finding
        );
        String retrievalMethod = properties.isEnableHybridSearch()
                ? "HYBRID"
                : "VECTOR";

        finding.setRagQuery(query);
        agentFindingRepository.save(finding);

        List<KnowledgeSearchResult> searchResults =
                knowledgeSearchService.search(
                        projectId,
                        query,
                        properties.getSearchLimit(),
                        properties.isEnableHybridSearch()
                );

        List<KnowledgeSearchResult> rankedResults = rankAndFilter(
                searchResults
        );
        List<InvestigationEvidenceItem> evidenceItems = new ArrayList<>();

        for (KnowledgeSearchResult searchResult : rankedResults) {
            citationService.persistCitation(
                    finding,
                    searchResult,
                    retrievalMethod
            ).ifPresent(citation ->
                    evidenceItems.add(toEvidenceItem(
                            agentType,
                            searchResult,
                            retrievalMethod,
                            matchedReason,
                            citation
                    ))
            );

            if (evidenceItems.size()
                    >= properties.getMaxCitationsPerAgent()) {
                break;
            }
        }

        return List.copyOf(evidenceItems);
    }

    List<KnowledgeSearchResult> rankAndFilter(
            List<KnowledgeSearchResult> searchResults
    ) {
        BigDecimal minimumScore = properties.getMinimumRelevanceScore();
        Set<UUID> seenChunks = new LinkedHashSet<>();
        List<KnowledgeSearchResult> filtered = searchResults.stream()
                .filter(result -> result.similarity()
                        >= minimumScore.doubleValue())
                .sorted(Comparator.comparingDouble(
                        KnowledgeSearchResult::similarity
                ).reversed())
                .filter(result -> seenChunks.add(result.chunkId()))
                .toList();

        return selectDiverseResults(
                filtered,
                properties.getMaxCitationsPerAgent()
        );
    }

    List<KnowledgeSearchResult> selectDiverseResults(
            List<KnowledgeSearchResult> rankedResults,
            int maxResults
    ) {
        if (rankedResults.isEmpty() || maxResults <= 0) {
            return List.of();
        }

        List<KnowledgeSearchResult> selected = new ArrayList<>();
        Set<UUID> usedDocuments = new LinkedHashSet<>();

        for (KnowledgeSearchResult result : rankedResults) {
            if (usedDocuments.add(result.documentId())) {
                selected.add(result);
            }
            if (selected.size() >= maxResults) {
                return List.copyOf(selected);
            }
        }

        for (KnowledgeSearchResult result : rankedResults) {
            if (selected.stream().anyMatch(
                    item -> item.chunkId().equals(result.chunkId())
            )) {
                continue;
            }
            selected.add(result);
            if (selected.size() >= maxResults) {
                break;
            }
        }

        return List.copyOf(selected);
    }

    private InvestigationEvidenceItem toEvidenceItem(
            String agentType,
            KnowledgeSearchResult searchResult,
            String retrievalMethod,
            String matchedReason,
            AgentFindingCitation citation
    ) {
        return new InvestigationEvidenceItem(
                agentType,
                searchResult.documentId(),
                searchResult.fileName(),
                searchResult.chunkId(),
                searchResult.content(),
                searchResult.similarity(),
                retrievalMethod,
                Map.of(
                        "citationId", citation.getId(),
                        "chunkIndex", searchResult.chunkIndex()
                ),
                matchedReason
        );
    }

    private AgentFinding latestCompletedFinding(
            UUID investigationId,
            String agentType
    ) {
        return agentFindingRepository
                .findByInvestigationCase_IdAndAgentType(
                        investigationId,
                        agentType
                )
                .stream()
                .filter(finding -> "COMPLETE".equals(finding.getStatus()))
                .max(Comparator.comparing(AgentFinding::getCreatedAt))
                .orElse(null);
    }
}
