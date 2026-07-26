package com.umeshowl.banking.investigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentFindingRepository
        extends JpaRepository<AgentFinding, UUID> {

    List<AgentFinding>
            findByInvestigationCase_IdOrderByCreatedAtAsc(
                    UUID caseId
            );

    List<AgentFinding>
            findByInvestigationCase_IdAndAgentType(
                    UUID caseId,
                    String agentType
            );

    List<AgentFinding>
            findByInvestigationCase_IdAndStatus(
                    UUID caseId,
                    String status
            );

    @Query("""
            SELECT finding.agentType,
                   finding.status,
                   COUNT(finding),
                   AVG(
                       (EXTRACT(EPOCH FROM finding.completedAt)
                           - EXTRACT(EPOCH FROM finding.startedAt)) * 1000
                   )
            FROM AgentFinding finding
            WHERE finding.investigationCase.project.id = :projectId
            GROUP BY finding.agentType, finding.status
            """)
    List<Object[]> aggregateByAgentTypeAndStatus(
            @Param("projectId") UUID projectId
    );
}
