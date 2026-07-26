package com.umeshowl.banking.investigation.report;

import java.util.Optional;
import java.util.UUID;

public interface InvestigationReportStore {

    InvestigationReport save(
            UUID investigationId,
            InvestigationReport report,
            String rawLlmResponse
    );

    Optional<InvestigationReport> findLatest(UUID investigationId);
}
