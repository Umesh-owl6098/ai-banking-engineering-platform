package com.umeshowl.banking.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HumanReviewDecisionRepository
        extends JpaRepository<HumanReviewDecision, UUID> {

    List<HumanReviewDecision>
            findByInvestigationCase_IdOrderByDecisionAtAsc(
                    UUID caseId
            );

    boolean existsByInvestigationCase_IdAndDecisionIn(
            UUID caseId,
            Collection<String> decisions
    );
}
