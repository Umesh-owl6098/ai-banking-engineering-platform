package com.umeshowl.banking.investigation.assignment.dto;

import java.util.List;

public record AnalystQueueResponse(
        List<AnalystQueueItemResponse> myQueue,
        List<AnalystQueueItemResponse> unassigned,
        List<AnalystQueueItemResponse> inReview,
        List<AnalystQueueItemResponse> escalated,
        List<AnalystQueueItemResponse> allAssigned
) {
}
