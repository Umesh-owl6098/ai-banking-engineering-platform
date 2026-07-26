package com.umeshowl.banking.investigation.report;

import java.time.OffsetDateTime;

public record InvestigationReportMetadata(
        String promptVersion,
        OffsetDateTime generatedAt,
        String modelName,
        long generationDurationMs,
        String generationMode
) {
}
