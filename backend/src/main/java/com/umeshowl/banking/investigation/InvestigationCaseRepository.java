package com.umeshowl.banking.investigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InvestigationCaseRepository
        extends JpaRepository<InvestigationCase, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InvestigationCase investigationCase
            SET investigationCase.status = 'RUNNING',
                investigationCase.updatedAt = :now,
                investigationCase.executionFailureStage = null,
                investigationCase.executionFailureMessage = null,
                investigationCase.executionFailureAt = null
            WHERE investigationCase.id = :caseId
              AND investigationCase.status IN ('NEW', 'EXECUTION_FAILED')
            """)
    int beginAutoExecution(
            @Param("caseId") UUID caseId,
            @Param("now") OffsetDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InvestigationCase investigationCase
            SET investigationCase.status = 'EXECUTION_FAILED',
                investigationCase.executionFailureStage = :stage,
                investigationCase.executionFailureMessage = :message,
                investigationCase.executionFailureAt = :failedAt,
                investigationCase.updatedAt = :failedAt
            WHERE investigationCase.id = :caseId
              AND investigationCase.status IN ('NEW', 'RUNNING')
            """)
    int markExecutionFailed(
            @Param("caseId") UUID caseId,
            @Param("stage") String stage,
            @Param("message") String message,
            @Param("failedAt") OffsetDateTime failedAt
    );

    List<InvestigationCase>
            findByProject_IdOrderByCreatedAtDesc(
                    UUID projectId
            );

    List<InvestigationCase>
            findByProject_IdAndStatusOrderByCreatedAtDesc(
                    UUID projectId,
                    String status
            );

    List<InvestigationCase>
            findByCustomer_IdOrderByCreatedAtDesc(
                    UUID customerId
            );

    List<InvestigationCase>
            findByTransaction_IdOrderByCreatedAtDesc(
                    UUID transactionId
            );

    boolean existsByTransaction_Id(UUID transactionId);

    boolean existsByScenarioGroupId(String scenarioGroupId);

    java.util.Optional<InvestigationCase> findFirstByScenarioGroupIdOrderByCreatedAtDesc(
            String scenarioGroupId
    );

    long countByStatus(String status);

    long countByStatusNot(String status);

    long countByProject_IdAndStatus(UUID projectId, String status);

    long countByProject_IdAndPriority(UUID projectId, String priority);

    long countByProject_IdAndCreatedAtGreaterThanEqual(
            UUID projectId,
            OffsetDateTime createdAt
    );

    List<InvestigationCase> findTop10ByProject_IdAndStatusOrderByUpdatedAtDesc(
            UUID projectId,
            String status
    );

    List<InvestigationCase>
            findByProject_IdAndStatusAndAssignedAnalystIdIsNullOrderByUpdatedAtAsc(
                    UUID projectId,
                    String status
            );

    List<InvestigationCase>
            findByProject_IdAndAssignedAnalystIdOrderByUpdatedAtAsc(
                    UUID projectId,
                    UUID assignedAnalystId
            );

    List<InvestigationCase>
            findByProject_IdAndStatusInOrderByUpdatedAtAsc(
                    UUID projectId,
                    List<String> statuses
            );

    List<InvestigationCase>
            findByProject_IdAndAssignedAnalystIdIsNotNullOrderByAssignedAtDesc(
                    UUID projectId
            );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InvestigationCase investigationCase
            SET investigationCase.assignedAnalystId = :analystId,
                investigationCase.assignedAnalystUsername = :username,
                investigationCase.assignedAt = :assignedAt,
                investigationCase.assignmentNotes = :notes,
                investigationCase.status = 'ASSIGNED',
                investigationCase.updatedAt = :assignedAt
            WHERE investigationCase.id = :caseId
              AND investigationCase.assignedAnalystId IS NULL
              AND investigationCase.status = 'AWAITING_REVIEW'
            """)
    int claimUnassignedCase(
            @Param("caseId") UUID caseId,
            @Param("analystId") UUID analystId,
            @Param("username") String username,
            @Param("assignedAt") OffsetDateTime assignedAt,
            @Param("notes") String notes
    );

    @Query("""
            SELECT investigationCase.status, COUNT(investigationCase)
            FROM InvestigationCase investigationCase
            WHERE investigationCase.project.id = :projectId
            GROUP BY investigationCase.status
            """)
    List<Object[]> countByProjectGroupedByStatus(
            @Param("projectId") UUID projectId
    );

    @Query("""
            SELECT investigationCase.priority, COUNT(investigationCase)
            FROM InvestigationCase investigationCase
            WHERE investigationCase.project.id = :projectId
            GROUP BY investigationCase.priority
            """)
    List<Object[]> countByProjectGroupedByPriority(
            @Param("projectId") UUID projectId
    );

    @Query("""
            SELECT AVG(
                (EXTRACT(EPOCH FROM investigationCase.updatedAt)
                    - EXTRACT(EPOCH FROM investigationCase.createdAt)) * 1000
            )
            FROM InvestigationCase investigationCase
            WHERE investigationCase.project.id = :projectId
              AND investigationCase.status IN (
                  'AWAITING_REVIEW', 'APPROVED', 'REJECTED', 'CLOSED', 'ESCALATED'
              )
            """)
    Double averageCompletedDurationMs(@Param("projectId") UUID projectId);

    List<InvestigationCase> findByProject_IdAndStatusInOrderByUpdatedAtDesc(
            UUID projectId,
            List<String> statuses
    );

    List<InvestigationCase> findTop12ByProject_IdOrderByCreatedAtDesc(
            UUID projectId
    );

    List<InvestigationCase> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            List<String> statuses,
            OffsetDateTime updatedBefore
    );
}
