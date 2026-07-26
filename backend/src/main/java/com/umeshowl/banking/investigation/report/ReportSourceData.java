package com.umeshowl.banking.investigation.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportSourceData(
        Map<String, Object> investigation,
        Map<String, Object> customer,
        Map<String, Object> transaction,
        Map<String, Object> fraudFinding,
        Map<String, Object> kycFinding,
        Map<String, Object> amlFinding,
        Map<String, Object> complianceFinding,
        List<Map<String, Object>> evidence
) {
    public ReportSourceData {
        investigation = safeCopy(investigation);
        customer = safeCopy(customer);
        transaction = safeCopy(transaction);
        fraudFinding = safeCopy(fraudFinding);
        kycFinding = safeCopy(kycFinding);
        amlFinding = safeCopy(amlFinding);
        complianceFinding = safeCopy(complianceFinding);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    private static Map<String, Object> safeCopy(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }
}
