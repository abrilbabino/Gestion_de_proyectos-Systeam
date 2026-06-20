package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.ProjectRejectedEvent;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Persists a notification row after the audit finding transaction commits.
 * Exceptions here do NOT roll back the parent transaction (already committed).
 */
@Component
public class ProjectRejectedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectRejectedEventListener.class);

    private final NotificationService notificationService;

    public ProjectRejectedEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectRejected(ProjectRejectedEvent event) {
        try {
            String payload = String.format(
                "{\"proyectoId\":%d,\"auditorId\":%d,\"findingId\":%d}",
                event.getProyectoId(),
                event.getAuditorId(),
                event.getFindingId()
            );
            notificationService.createNotification(
                event.getCreadorId(),
                "PROJECT_REJECTED",
                "Proyecto rechazado",
                "Tu proyecto ha sido rechazado en la auditar\u00eda.",
                payload
            );
            log.info("Notification persisted for rejected project {} (creador={})",
                event.getProyectoId(), event.getCreadorId());
        } catch (Exception e) {
            // Isolation: the finding and state change are already committed.
            // Log the error but do not rethrow — the main transaction must not be affected.
            log.error("Failed to persist notification for project {} rejection: {}",
                event.getProyectoId(), e.getMessage(), e);
        }
    }
}
