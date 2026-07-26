package com.umeshowl.banking.investigation.assignment;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.assignment.dto.AnalystQueueItemResponse;
import com.umeshowl.banking.investigation.assignment.dto.AnalystQueueResponse;
import com.umeshowl.banking.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalystQueueService {

    private static final Set<Role> SUPERVISOR_ROLES = Set.of(
            Role.ADMIN,
            Role.SUPERVISOR
    );

    private final InvestigationCaseRepository investigationCaseRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final UUID defaultProjectId;

    public AnalystQueueService(
            InvestigationCaseRepository investigationCaseRepository,
            ProjectRepository projectRepository,
            CurrentUserService currentUserService,
            @Value("${investigation.auto-create.default-project-id}")
            UUID defaultProjectId
    ) {
        this.investigationCaseRepository = investigationCaseRepository;
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.defaultProjectId = defaultProjectId;
    }

    @Transactional(readOnly = true)
    public AnalystQueueResponse loadQueue(UUID projectId) {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        UUID resolvedProjectId = resolveProjectId(projectId);

        List<AnalystQueueItemResponse> myQueue = List.of();
        if (currentUser.role() != Role.READ_ONLY) {
            myQueue = investigationCaseRepository
                    .findByProject_IdAndAssignedAnalystIdOrderByUpdatedAtAsc(
                            resolvedProjectId,
                            currentUser.id()
                    )
                    .stream()
                    .filter(item ->
                            Set.of("ASSIGNED", "IN_REVIEW").contains(item.getStatus())
                    )
                    .map(AnalystQueueItemResponse::from)
                    .toList();
        }

        List<AnalystQueueItemResponse> unassigned = investigationCaseRepository
                .findByProject_IdAndStatusAndAssignedAnalystIdIsNullOrderByUpdatedAtAsc(
                        resolvedProjectId,
                        "AWAITING_REVIEW"
                )
                .stream()
                .map(AnalystQueueItemResponse::from)
                .toList();

        List<AnalystQueueItemResponse> inReview = investigationCaseRepository
                .findByProject_IdAndStatusOrderByCreatedAtDesc(
                        resolvedProjectId,
                        "IN_REVIEW"
                )
                .stream()
                .sorted(Comparator.comparing(InvestigationCase::getUpdatedAt))
                .map(AnalystQueueItemResponse::from)
                .toList();

        List<AnalystQueueItemResponse> escalated = investigationCaseRepository
                .findByProject_IdAndStatusOrderByCreatedAtDesc(
                        resolvedProjectId,
                        "ESCALATED"
                )
                .stream()
                .sorted(Comparator.comparing(InvestigationCase::getUpdatedAt).reversed())
                .map(AnalystQueueItemResponse::from)
                .toList();

        List<AnalystQueueItemResponse> allAssigned = List.of();
        if (SUPERVISOR_ROLES.contains(currentUser.role())) {
            allAssigned = investigationCaseRepository
                    .findByProject_IdAndAssignedAnalystIdIsNotNullOrderByAssignedAtDesc(
                            resolvedProjectId
                    )
                    .stream()
                    .map(AnalystQueueItemResponse::from)
                    .toList();
        }

        return new AnalystQueueResponse(
                myQueue,
                unassigned,
                inReview,
                escalated,
                allAssigned
        );
    }

    private UUID resolveProjectId(UUID projectId) {
        UUID resolvedProjectId = projectId == null ? defaultProjectId : projectId;
        if (!projectRepository.existsById(resolvedProjectId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Project not found: " + resolvedProjectId
            );
        }
        return resolvedProjectId;
    }
}
