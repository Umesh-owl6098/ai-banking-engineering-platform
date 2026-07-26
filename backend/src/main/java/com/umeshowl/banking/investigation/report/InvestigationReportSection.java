package com.umeshowl.banking.investigation.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InvestigationReportSection(
        String title,
        String narrative,
        Map<String, Object> deterministicFacts
) {
    public InvestigationReportSection {
        deterministicFacts = Map.copyOf(
                deterministicFacts == null
                        ? Map.of()
                        : deterministicFacts
        );
    }

    public static InvestigationReportSection of(
            String title,
            String narrative
    ) {
        return new InvestigationReportSection(title, narrative, Map.of());
    }

    public InvestigationReportSection withDeterministicFacts(
            Map<String, Object> facts
    ) {
        Map<String, Object> merged = new LinkedHashMap<>(deterministicFacts);
        if (facts != null) {
            merged.putAll(facts);
        }
        return new InvestigationReportSection(title, narrative, merged);
    }
}
