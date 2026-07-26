package com.umeshowl.banking.investigation.review.dto;

import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import com.umeshowl.banking.investigation.report.InvestigationReport;

import java.util.List;

public record InvestigationReviewContextResponse(
        InvestigationCaseResponse investigation,
        InvestigationReport report,
        InvestigationReviewSummaryResponse reviewSummary,
        List<HumanReviewDecisionResponse> decisions,
        List<InvestigationTimelineEntryResponse> timeline
) {
    public InvestigationReviewContextResponse {
        decisions = List.copyOf(decisions);
        timeline = List.copyOf(timeline);
    }
}
