package com.umeshowl.banking.investigation.review.dto;

import com.umeshowl.banking.investigation.HumanReviewDecision;
import com.umeshowl.banking.investigation.review.ReviewDecisionAction;
import com.umeshowl.banking.investigation.review.ReviewNotesParser;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record HumanReviewDecisionResponse(
        UUID id,
        UUID investigationId,
        String reviewerId,
        String decision,
        ReviewDecisionAction action,
        String decisionReason,
        String comments,
        String additionalNotes,
        OffsetDateTime decisionAt
) {
    public static HumanReviewDecisionResponse from(
            HumanReviewDecision decision
    ) {
        Map<String, String> notes = ReviewNotesParser.parse(
                decision.getReason()
        );

        return new HumanReviewDecisionResponse(
                decision.getId(),
                decision.getInvestigationCase().getId(),
                decision.getReviewerId(),
                decision.getDecision(),
                ReviewNotesParser.actionFor(decision.getDecision(), notes),
                notes.get("decisionReason"),
                notes.get("comments"),
                notes.get("additionalNotes"),
                decision.getDecisionAt()
        );
    }
}
