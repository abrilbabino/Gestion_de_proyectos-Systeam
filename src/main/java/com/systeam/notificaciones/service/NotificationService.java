package com.systeam.notificaciones.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.systeam.notificaciones.Notification;
import com.systeam.notificaciones.dto.NotificationResponse;
import com.systeam.notificaciones.dto.UnreadCountResponse;
import com.systeam.notificaciones.repository.JdbcNotificationRepository;
import com.systeam.project.exception.ResourceNotFoundException;

/**
 * Business logic for in-app notifications.
 * Validates ownership before mutating notification state.
 */
@Service
public class NotificationService {

    private final JdbcNotificationRepository repository;

    public NotificationService(JdbcNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns a paginated list of the user's notifications, mapped to DTOs.
     */
    public Page<NotificationResponse> findByUserId(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable)
            .map(this::toResponse);
    }

    /**
     * Marks a notification as read if it exists and belongs to the user.
     * Idempotent: calling again for an already-read notification returns 200.
     *
     * @throws ResourceNotFoundException if the notification does not exist
     *                                   or belongs to another user
     */
    public void markAsRead(Long id, Long userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Notification not found with id: " + id));
        if (notification.getReadAt() == null) {
            repository.markAsRead(id, userId);
        }
    }

    /**
     * Returns the count of unread notifications for the user.
     */
    public UnreadCountResponse countUnread(Long userId) {
        return new UnreadCountResponse(repository.countUnread(userId));
    }

    /**
     * Thin delegator for listeners to persist a notification row.
     * No validation — messages are trusted from internal services.
     */
    public void createNotification(Long userId, String type, String title, String message, String payload) {
        repository.insert(userId, type, title, message, payload);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
            .id(n.getId())
            .type(n.getType())
            .title(n.getTitle())
            .message(n.getMessage())
            .payload(n.getPayload())
            .createdAt(n.getCreatedAt())
            .readAt(n.getReadAt())
            .build();
    }
}
