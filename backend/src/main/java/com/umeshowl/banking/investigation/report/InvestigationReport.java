package com.umeshowl.banking.investigation.report;

import java.util.List;
import java.util.UUID;

public record InvestigationReport(
        UUID id,
        UUID investigationId,
        InvestigationReportMetadata metadata,
        String executiveSummary,
        InvestigationReportSection investigationOverview,
        InvestigationReportSection customerRiskProfile,
        InvestigationReportSection fraudAnalysis,
        InvestigationReportSection kycAnalysis,
        InvestigationReportSection amlAnalysis,
        InvestigationReportSection complianceAssessment,
        List<InvestigationReportSection> supportingEvidence,
        String analystRecommendation,
        String confidenceExplanation,
        String limitations
) {
    public InvestigationReport {
        supportingEvidence = List.copyOf(supportingEvidence);
    }
}
