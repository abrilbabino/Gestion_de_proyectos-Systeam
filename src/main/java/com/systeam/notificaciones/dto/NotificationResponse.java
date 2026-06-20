package com.systeam.notificaciones.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Public DTO for notification data exposed via REST.
 * Deliberately omits {@code recipientUserId} to prevent leaking
 * the notification owner to other authenticated users.
 */
@Data
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String message;
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
