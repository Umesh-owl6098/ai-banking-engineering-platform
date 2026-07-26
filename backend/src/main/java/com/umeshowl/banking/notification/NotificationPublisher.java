package com.umeshowl.banking.notification;

import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.execution.InvestigationExecutionEvent;
import com.umeshowl.banking.investigation.execution.InvestigationExecutionEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationPublisher {

    private static final Set<Role> SUPERVISOR_ROLES = Set.of(
            Role.ADMIN,
            Role.SUPERVISOR
    );

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final InvestigationCaseService investigationCaseService;

    public NotificationPublisher(
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            InvestigationCaseService investigationCaseService
    ) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.investigationCaseService = investigationCaseService;
    }

    @Transactional
    public void notifyCriticalInvestigationCreated(
            InvestigationCase investigationCase
    ) {
        if (!"CRITICAL".equalsIgnoreCase(investigationCase.getPriority())) {
            return;
        }

        String title = "Critical investigation created";
        String message = investigationCase.getTitle()
                + " requires immediate supervisor attention.";
        notifySupervisors(
                investigationCase,
                NotificationType.CRITICAL_INVESTIGATION_CREATED,
                NotificationSeverity.CRITICAL,
                title,
                message
        );
    }

    @Transactional
    public void notifyAssigned(
            InvestigationCase investigationCase,
            User assignee,
            boolean reassignment
    ) {
        NotificationType type = reassignment
                ? NotificationType.INVESTIGATION_REASSIGNED
                : NotificationType.INVESTIGATION_ASSIGNED;
        String title = reassignment
                ? "Investigation reassigned to you"
                : "Investigation assigned to you";
        String message = investigationCase.getTitle()
                + " has been assigned to you for review.";

        notificationService.createNotification(
                assignee.getId(),
                title,
                message,
                type,
                NotificationSeverity.INFO,
                investigationCase,
                investigationCase.getTransaction()
        );
    }

    @Transactional
    public void notifyClaimed(
            InvestigationCase investigationCase,
            User claimer
    ) {
        notificationService.createNotification(
                claimer.getId(),
                "Investigation claimed",
                "You claimed "
                        + investigationCase.getTitle()
                        + " for review.",
                NotificationType.INVESTIGATION_CLAIMED,
                NotificationSeverity.INFO,
                investigationCase,
                investigationCase.getTransaction()
        );

        String supervisorMessage = claimer.getUsername()
                + " claimed "
                + investigationCase.getTitle()
                + ".";
        notifySupervisorsExcept(
                investigationCase,
                NotificationType.INVESTIGATION_CLAIMED,
                NotificationSeverity.INFO,
                "Investigation claimed",
                supervisorMessage,
                claimer.getId()
        );
    }

    @Transactional
    public void notifyEscalated(
            InvestigationCase investigationCase,
            String reviewerUsername
    ) {
        String message = investigationCase.getTitle()
                + " was escalated by "
                + reviewerUsername
                + ".";
        notifySupervisors(
                investigationCase,
                NotificationType.INVESTIGATION_ESCALATED,
                NotificationSeverity.WARNING,
                "Investigation escalated",
                message
        );

        if (investigationCase.getAssignedAnalystId() != null) {
            notificationService.createNotification(
                    investigationCase.getAssignedAnalystId(),
                    "Investigation escalated",
                    message,
                    NotificationType.INVESTIGATION_ESCALATED,
                    NotificationSeverity.WARNING,
                    investigationCase,
                    investigationCase.getTransaction()
            );
        }
    }

    @Transactional
    public void notifyReportGenerated(UUID investigationId) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        String title = "Investigation report generated";
        String message = "The investigation report for "
                + investigationCase.getTitle()
                + " is ready for review.";

        notifyAssignedOrSupervisors(
                investigationCase,
                NotificationType.REPORT_GENERATED,
                NotificationSeverity.INFO,
                title,
                message
        );
    }

    @Transactional
    public void notifyExecutionFailed(
            UUID investigationId,
            String failureMessage
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        String title = "AI execution failure";
        String message = "Investigation "
                + investigationCase.getTitle()
                + " failed during AI execution"
                + (failureMessage == null || failureMessage.isBlank()
                        ? "."
                        : ": " + failureMessage);

        notifyAssignedOrSupervisors(
                investigationCase,
                NotificationType.AI_EXECUTION_FAILURE,
                NotificationSeverity.CRITICAL,
                title,
                message
        );
    }

    @Transactional
    public void notifyOpenAiFallback(UUID investigationId) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);
        String title = "OpenAI fallback mode";
        String message = "Report generation for "
                + investigationCase.getTitle()
                + " used deterministic fallback because OpenAI was unavailable.";

        notifySupervisors(
                investigationCase,
                NotificationType.OPENAI_FALLBACK_MODE,
                NotificationSeverity.WARNING,
                title,
                message
        );
    }

    @Transactional
    public void notifyWaitingTooLong(InvestigationCase investigationCase) {
        OffsetDateTime dedupeWindow =
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(24);
        String title = "Investigation waiting too long";
        String message = investigationCase.getTitle()
                + " has been awaiting review longer than expected.";

        if (investigationCase.getAssignedAnalystId() != null) {
            if (notificationRepository
                    .existsByUser_IdAndTypeAndRelatedInvestigation_IdAndCreatedAtAfter(
                            investigationCase.getAssignedAnalystId(),
                            NotificationType.INVESTIGATION_WAITING_TOO_LONG,
                            investigationCase.getId(),
                            dedupeWindow
                    )) {
                return;
            }

            notificationService.createNotification(
                    investigationCase.getAssignedAnalystId(),
                    title,
                    message,
                    NotificationType.INVESTIGATION_WAITING_TOO_LONG,
                    NotificationSeverity.WARNING,
                    investigationCase,
                    investigationCase.getTransaction()
            );
            return;
        }

        notifySupervisors(
                investigationCase,
                NotificationType.INVESTIGATION_WAITING_TOO_LONG,
                NotificationSeverity.WARNING,
                title,
                message
        );
    }

    @Transactional
    public void handleExecutionEvent(InvestigationExecutionEvent event) {
        if (event.eventType() == InvestigationExecutionEventType.REPORT_GENERATED) {
            notifyReportGenerated(event.investigationId());
            return;
        }

        if (event.eventType() == InvestigationExecutionEventType.EXECUTION_FAILED) {
            notifyExecutionFailed(
                    event.investigationId(),
                    event.message()
            );
        }
    }

    private void notifyAssignedOrSupervisors(
            InvestigationCase investigationCase,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String message
    ) {
        if (investigationCase.getAssignedAnalystId() != null) {
            notificationService.createNotification(
                    investigationCase.getAssignedAnalystId(),
                    title,
                    message,
                    type,
                    severity,
                    investigationCase,
                    investigationCase.getTransaction()
            );
            return;
        }

        notifySupervisors(
                investigationCase,
                type,
                severity,
                title,
                message
        );
    }

    private void notifySupervisors(
            InvestigationCase investigationCase,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String message
    ) {
        notifySupervisorsExcept(
                investigationCase,
                type,
                severity,
                title,
                message,
                null
        );
    }

    private void notifySupervisorsExcept(
            InvestigationCase investigationCase,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String message,
            UUID excludedUserId
    ) {
        for (User supervisor : loadSupervisors()) {
            if (excludedUserId != null && supervisor.getId().equals(excludedUserId)) {
                continue;
            }
            notificationService.createNotification(
                    supervisor.getId(),
                    title,
                    message,
                    type,
                    severity,
                    investigationCase,
                    investigationCase.getTransaction()
            );
        }
    }

    private List<User> loadSupervisors() {
        return userRepository.findByRoleInAndEnabledTrue(SUPERVISOR_ROLES);
    }
}
