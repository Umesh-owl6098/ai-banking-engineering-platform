package com.umeshowl.banking.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvestigationCaseEventRepository
        extends JpaRepository<InvestigationCaseEvent, UUID> {

    List<InvestigationCaseEvent>
            findByInvestigationCase_IdOrderByCreatedAtAsc(
                    UUID caseId
            );
}
