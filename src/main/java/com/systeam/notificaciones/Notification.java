package com.systeam.notificaciones;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Domain POJO representing a single notification row.
 * Maps 1:1 to the {@code notificaciones} table columns.
 */
@Data
@Builder
public class Notification {

    private Long id;
    private Long recipientUserId;
    private String type;
    private String payload;
    private String title;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
