package com.umeshowl.banking.investigation.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationReportRepository
        extends JpaRepository<InvestigationReportEntity, UUID> {

    List<InvestigationReportEntity>
            findByInvestigationCase_IdOrderByGeneratedAtDesc(
                    UUID caseId
            );

    Optional<InvestigationReportEntity>
            findFirstByInvestigationCase_IdOrderByGeneratedAtDesc(
                    UUID caseId
            );
}
