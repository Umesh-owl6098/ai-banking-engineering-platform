package com.umeshowl.banking.investigation.compliance;
import java.util.Map;
public record ComplianceIndicator(ComplianceIndicatorType type, int scoreContribution, String explanation, Map<String,Object> evidenceValues) {
    public ComplianceIndicator { evidenceValues=Map.copyOf(evidenceValues); }
}
