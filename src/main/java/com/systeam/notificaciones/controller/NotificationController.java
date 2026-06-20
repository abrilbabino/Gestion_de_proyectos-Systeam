package com.systeam.notificaciones.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.notificaciones.dto.NotificationResponse;
import com.systeam.notificaciones.dto.UnreadCountResponse;
import com.systeam.notificaciones.service.NotificationService;
import com.systeam.security.JwtPrincipal;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Returns the authenticated user's notifications, most recent first.
     */
    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.findByUserId(
            principal.userId(), PageRequest.of(page, size));
    }

    /**
     * Marks a notification as read. Returns 200 if successful,
     * 404 if the notification does not exist or belongs to another user.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        notificationService.markAsRead(id, principal.userId());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return notificationService.countUnread(principal.userId());
    }
}
