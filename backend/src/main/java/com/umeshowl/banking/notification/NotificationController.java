package com.umeshowl.banking.notification;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.notification.dto.NotificationPageResponse;
import com.umeshowl.banking.notification.dto.NotificationResponse;
import com.umeshowl.banking.notification.dto.UnreadCountResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationEventHub notificationEventHub;
    private final CurrentUserService currentUserService;

    public NotificationController(
            NotificationService notificationService,
            NotificationEventHub notificationEventHub,
            CurrentUserService currentUserService
    ) {
        this.notificationService = notificationService;
        this.notificationEventHub = notificationEventHub;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public NotificationPageResponse listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId
    ) {
        return notificationService.listNotifications(page, size, userId);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public UnreadCountResponse unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public NotificationResponse markRead(@PathVariable UUID notificationId) {
        return notificationService.markRead(notificationId);
    }

    @PostMapping("/read-all")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public UnreadCountResponse markAllRead() {
        return notificationService.markAllRead();
    }

    @GetMapping(
            value = "/live",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public SseEmitter notificationLiveStream() {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        return notificationEventHub.subscribe(currentUser.id());
    }
}
