package com.umeshowl.banking.notification;

import com.umeshowl.banking.auth.AuthenticatedUser;
import com.umeshowl.banking.auth.CurrentUserService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.notification.dto.NotificationPageResponse;
import com.umeshowl.banking.notification.dto.NotificationResponse;
import com.umeshowl.banking.notification.dto.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationEventHub notificationEventHub;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            NotificationEventHub notificationEventHub
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.notificationEventHub = notificationEventHub;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(
            int page,
            int size,
            UUID userIdFilter
    ) {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        Page<Notification> notifications;
        if (currentUser.role() == Role.ADMIN && userIdFilter != null) {
            notifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(
                    userIdFilter,
                    pageable
            );
        } else if (currentUser.role() == Role.ADMIN && userIdFilter == null) {
            notifications = notificationRepository.findAllByOrderByCreatedAtDesc(
                    pageable
            );
        } else {
            notifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(
                    currentUser.id(),
                    pageable
            );
        }

        return NotificationPageResponse.from(
                notifications.map(NotificationResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        long count = currentUser.role() == Role.ADMIN
                ? notificationRepository.countByReadFalse()
                : notificationRepository.countByUser_IdAndReadFalse(
                        currentUser.id()
                );
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"
                ));

        if (currentUser.role() != Role.ADMIN
                && !notification.getUser().getId().equals(currentUser.id())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to update this notification"
            );
        }

        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public UnreadCountResponse markAllRead() {
        AuthenticatedUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.role() == Role.ADMIN) {
            notificationRepository.markAllRead();
        } else {
            notificationRepository.markAllReadForUser(currentUser.id());
        }
        return unreadCount();
    }

    @Transactional
    public NotificationResponse createNotification(
            UUID userId,
            String title,
            String message,
            NotificationType type,
            NotificationSeverity severity,
            InvestigationCase investigationCase,
            MockTransaction transaction
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification recipient not found: " + userId
                ));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setSeverity(severity);
        notification.setRelatedInvestigation(investigationCase);
        notification.setRelatedTransaction(transaction);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = NotificationResponse.from(saved);
        notificationEventHub.publish(userId, response);
        return response;
    }
}
