package com.umeshowl.banking.investigation.evidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvestigationEvidenceResult(
        UUID investigationId,
        Map<String, List<InvestigationEvidenceItem>> evidenceByAgent,
        Map<String, Integer> citationCountsByAgent,
        int totalCitationCount,
        List<String> warnings
) {
    public InvestigationEvidenceResult {
        evidenceByAgent = Map.copyOf(evidenceByAgent);
        citationCountsByAgent = Map.copyOf(citationCountsByAgent);
        warnings = List.copyOf(warnings);
    }

    public static InvestigationEvidenceResult empty(
            UUID investigationId,
            List<String> warnings
    ) {
        return new InvestigationEvidenceResult(
                investigationId,
                Map.of(),
                Map.of(),
                0,
                warnings
        );
    }

    public static Map<String, Integer> countsFrom(
            Map<String, List<InvestigationEvidenceItem>> evidenceByAgent
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        evidenceByAgent.forEach(
                (agentType, items) -> counts.put(agentType, items.size())
        );
        return counts;
    }
}
