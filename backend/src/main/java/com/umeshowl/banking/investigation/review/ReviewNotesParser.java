package com.umeshowl.banking.investigation.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.review.dto.HumanReviewNotesRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReviewNotesParser {

    private ReviewNotesParser() {
    }

    static String serialize(
            ReviewDecisionAction action,
            HumanReviewNotesRequest notes
    ) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("action", action.name());
        payload.put("decisionReason", notes.decisionReason());
        if (notes.comments() != null && !notes.comments().isBlank()) {
            payload.put("comments", notes.comments().trim());
        }
        if (notes.additionalNotes() != null
                && !notes.additionalNotes().isBlank()) {
            payload.put(
                    "additionalNotes",
                    notes.additionalNotes().trim()
            );
        }

        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize review notes",
                    exception
            );
        }
    }

    public static Map<String, String> parse(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of();
        }

        if (!reason.trim().startsWith("{")) {
            return Map.of("decisionReason", reason.trim());
        }

        try {
            Map<String, String> parsed = new ObjectMapper().readValue(
                    reason,
                    new TypeReference<>() {
                    }
            );
            return Map.copyOf(parsed);
        } catch (Exception exception) {
            return Map.of("decisionReason", reason.trim());
        }
    }

    public static ReviewDecisionAction actionFor(
            String persistedDecision,
            Map<String, String> notes
    ) {
        String action = notes.get("action");
        if (action != null && !action.isBlank()) {
            return ReviewDecisionAction.valueOf(action);
        }

        return switch (persistedDecision) {
            case "APPROVED" -> ReviewDecisionAction.APPROVE;
            case "REJECTED" -> ReviewDecisionAction.REJECT;
            case "ESCALATED" -> ReviewDecisionAction.ESCALATE;
            case "NOTE_ADDED" -> ReviewDecisionAction.REQUEST_MORE_INVESTIGATION;
            default -> ReviewDecisionAction.APPROVE;
        };
    }

    static String persistedDecisionFor(ReviewDecisionAction action) {
        return switch (action) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case ESCALATE -> "ESCALATED";
            case REQUEST_MORE_INVESTIGATION -> "NOTE_ADDED";
        };
    }
}
