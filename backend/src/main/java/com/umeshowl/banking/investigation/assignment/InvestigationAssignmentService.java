package com.umeshowl.banking.investigation.assignment;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.assignment.dto.AssignInvestigationRequest;
import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import com.umeshowl.banking.investigation.review.InvestigationAuditEventTypes;
import com.umeshowl.banking.investigation.review.InvestigationAuditService;
import com.umeshowl.banking.notification.NotificationPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvestigationAssignmentService {

    private static final Set<String> ASSIGNABLE_STATUSES = Set.of(
            "AWAITING_REVIEW",
            "ASSIGNED",
            "ESCALATED"
    );

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "APPROVED",
            "REJECTED",
            "CLOSED"
    );

    private static final Set<Role> ANALYST_ROLES = Set.of(
            Role.FRAUD_ANALYST,
            Role.COMPLIANCE_ANALYST
    );

    private static final Set<Role> SUPERVISOR_ROLES = Set.of(
            Role.ADMIN,
            Role.SUPERVISOR
    );

    private final InvestigationCaseService investigationCaseService;
    private final InvestigationCaseRepository investigationCaseRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final InvestigationAuditService auditService;
    private final NotificationPublisher notificationPublisher;

    public InvestigationAssignmentService(
            InvestigationCaseService investigationCaseService,
            InvestigationCaseRepository investigationCaseRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            InvestigationAuditService auditService,
            NotificationPublisher notificationPublisher
    ) {
        this.investigationCaseService = investigationCaseService;
        this.investigationCaseRepository = investigationCaseRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public InvestigationCaseResponse assign(
            UUID investigationId,
            AssignInvestigationRequest request
    ) {
        AuthenticatedUser actor = currentUserService.requireCurrentUser();
        requireSupervisor(actor);

        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        validateAssignable(investigationCase);

        User assignee = resolveAnalystUser(request.assigneeUsername());
        boolean reassignment = investigationCase.getAssignedAnalystId() != null;
        String previousAssignee = investigationCase.getAssignedAnalystUsername();

        applyAssignment(
                investigationCase,
                assignee,
                request.notes(),
                "ASSIGNED"
        );

        auditService.recordEvent(
                investigationCase,
                reassignment
                        ? InvestigationAuditEventTypes.INVESTIGATION_REASSIGNED
                        : InvestigationAuditEventTypes.INVESTIGATION_ASSIGNED,
                actor.username(),
                assignmentPayload(assignee, request.notes(), previousAssignee)
        );

        InvestigationCase saved = investigationCaseRepository.save(investigationCase);
        notificationPublisher.notifyAssigned(saved, assignee, reassignment);
        return InvestigationCaseResponse.from(saved);
    }

    @Transactional
    public InvestigationCaseResponse unassign(UUID investigationId) {
        AuthenticatedUser actor = currentUserService.requireCurrentUser();
        requireSupervisor(actor);

        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        validateHasAssignment(investigationCase);

        if ("IN_REVIEW".equals(investigationCase.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot unassign an investigation that is in review"
            );
        }

        String previousAssignee = investigationCase.getAssignedAnalystUsername();
        clearAssignment(investigationCase);
        investigationCase.setStatus("AWAITING_REVIEW");

        auditService.recordEvent(
                investigationCase,
                InvestigationAuditEventTypes.INVESTIGATION_UNASSIGNED,
                actor.username(),
                Map.of(
                        "previousAssignee",
                        previousAssignee == null ? "" : previousAssignee
                )
        );

        return InvestigationCaseResponse.from(
                investigationCaseRepository.save(investigationCase)
        );
    }

    @Transactional
    public InvestigationCaseResponse claim(UUID investigationId) {
        AuthenticatedUser actor = currentUserService.requireCurrentUser();
        requireAnalyst(actor);

        User analyst = userRepository.findByUsername(actor.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated analyst not found"
                ));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = investigationCaseRepository.claimUnassignedCase(
                investigationId,
                analyst.getId(),
                analyst.getUsername(),
                now,
                null
        );

        if (updated == 0) {
            InvestigationCase current =
                    investigationCaseService.getCase(investigationId);
            if (current.getAssignedAnalystId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Investigation is already assigned to "
                                + current.getAssignedAnalystUsername()
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation is not eligible for claim: "
                            + current.getStatus()
            );
        }

        InvestigationCase claimed =
                investigationCaseService.getCase(investigationId);
        auditService.recordEvent(
                claimed,
                InvestigationAuditEventTypes.INVESTIGATION_CLAIMED,
                actor.username(),
                assignmentPayload(analyst, null, null)
        );

        notificationPublisher.notifyClaimed(claimed, analyst);
        return InvestigationCaseResponse.from(claimed);
    }

    public void validateAssignedReviewer(
            InvestigationCase investigationCase,
            AuthenticatedUser reviewer
    ) {
        if (SUPERVISOR_ROLES.contains(reviewer.role())) {
            return;
        }

        if (!ANALYST_ROLES.contains(reviewer.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Current role cannot perform review actions"
            );
        }

        if (investigationCase.getAssignedAnalystId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation must be assigned before analyst review"
            );
        }

        if (!reviewer.id().equals(investigationCase.getAssignedAnalystId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Investigation is assigned to another analyst"
            );
        }
    }

    public boolean isSupervisor(AuthenticatedUser user) {
        return SUPERVISOR_ROLES.contains(user.role());
    }

    @Transactional
    public void clearAssignmentFields(UUID investigationId) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        clearAssignment(investigationCase);
        investigationCaseRepository.save(investigationCase);
    }

    public List<User> listAssignableAnalysts() {
        return userRepository.findByRoleInAndEnabledTrue(
                List.of(Role.FRAUD_ANALYST, Role.COMPLIANCE_ANALYST)
        );
    }

    private void applyAssignment(
            InvestigationCase investigationCase,
            User assignee,
            String notes,
            String targetStatus
    ) {
        investigationCase.setAssignedAnalystId(assignee.getId());
        investigationCase.setAssignedAnalystUsername(assignee.getUsername());
        investigationCase.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));
        investigationCase.setAssignmentNotes(normalizeNotes(notes));
        investigationCase.setStatus(targetStatus);
    }

    private void clearAssignment(InvestigationCase investigationCase) {
        investigationCase.setAssignedAnalystId(null);
        investigationCase.setAssignedAnalystUsername(null);
        investigationCase.setAssignedAt(null);
        investigationCase.setAssignmentNotes(null);
        investigationCase.setReviewStartedAt(null);
    }

    private void validateAssignable(InvestigationCase investigationCase) {
        if (TERMINAL_STATUSES.contains(investigationCase.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot assign an investigation with final status: "
                            + investigationCase.getStatus()
            );
        }

        if ("IN_REVIEW".equals(investigationCase.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot reassign while investigation is in review"
            );
        }

        if (!ASSIGNABLE_STATUSES.contains(investigationCase.getStatus())
                && investigationCase.getAssignedAnalystId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation is not eligible for assignment: "
                            + investigationCase.getStatus()
            );
        }
    }

    private void validateHasAssignment(InvestigationCase investigationCase) {
        if (investigationCase.getAssignedAnalystId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation is not assigned"
            );
        }
    }

    private User resolveAnalystUser(String username) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Analyst not found: " + username
                ));

        if (!ANALYST_ROLES.contains(user.getRole()) && !SUPERVISOR_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User cannot be assigned investigations: " + username
            );
        }

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assigned user is disabled: " + username
            );
        }

        return user;
    }

    private Map<String, Object> assignmentPayload(
            User assignee,
            String notes,
            String previousAssignee
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assignee.getId().toString());
        payload.put("assigneeUsername", assignee.getUsername());
        payload.put("assigneeRole", assignee.getRole().name());
        if (notes != null && !notes.isBlank()) {
            payload.put("notes", notes.trim());
        }
        if (previousAssignee != null && !previousAssignee.isBlank()) {
            payload.put("previousAssignee", previousAssignee);
        }
        return payload;
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private void requireSupervisor(AuthenticatedUser user) {
        if (!SUPERVISOR_ROLES.contains(user.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only supervisors can manage investigation assignment"
            );
        }
    }

    private void requireAnalyst(AuthenticatedUser user) {
        if (!ANALYST_ROLES.contains(user.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only analysts can claim investigations"
            );
        }
    }
}
