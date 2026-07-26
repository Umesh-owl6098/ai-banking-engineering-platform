package com.umeshowl.banking.investigation.compliance;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record ComplianceAnalysisResult(UUID investigationId, int overallScore, ComplianceRiskLevel riskLevel, String recommendation, String summary, List<ComplianceIndicator> contributingFindings, OffsetDateTime analyzedAt) {
    public ComplianceAnalysisResult { contributingFindings=List.copyOf(contributingFindings); }
}
