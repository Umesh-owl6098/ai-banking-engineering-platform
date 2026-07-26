package com.umeshowl.banking.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentFindingCitationRepository
        extends JpaRepository<AgentFindingCitation, UUID> {

    List<AgentFindingCitation>
            findByFinding_IdOrderByCreatedAtAsc(
                    UUID findingId
            );

    boolean existsByFinding_IdAndChunk_Id(
            UUID findingId,
            UUID chunkId
    );
}
