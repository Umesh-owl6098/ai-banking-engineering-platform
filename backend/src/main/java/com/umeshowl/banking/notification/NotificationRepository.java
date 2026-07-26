package com.umeshowl.banking.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByUser_IdAndReadFalse(UUID userId);

    long countByReadFalse();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification notification
            SET notification.read = true
            WHERE notification.user.id = :userId
              AND notification.read = false
            """)
    int markAllReadForUser(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification notification SET notification.read = true")
    int markAllRead();

    boolean existsByUser_IdAndTypeAndRelatedInvestigation_IdAndCreatedAtAfter(
            UUID userId,
            NotificationType type,
            UUID investigationId,
            OffsetDateTime createdAfter
    );
}
