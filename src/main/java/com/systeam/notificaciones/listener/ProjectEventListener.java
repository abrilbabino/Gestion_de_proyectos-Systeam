package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.ProjectAuditedEvent;
import com.systeam.notificaciones.event.ProjectStateChangedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for project-related events after the transaction commits.
 * <ul>
 *   <li>{@link ProjectStateChangedEvent} — in-app notification + email on key transitions</li>
 *   <li>{@link ProjectAuditedEvent} — in-app notification + email to project creator</li>
 * </ul>
 */
@Component
public class ProjectEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectEventListener.class);

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    public ProjectEventListener(NotificationService notificationService, EmailService emailService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectStateChanged(ProjectStateChangedEvent event) {
        try {
            String projectName = jdbc.queryForObject(
                "SELECT titulo FROM projects WHERE id = ?", String.class, event.getProyectoId()
            );
            Long creatorId = jdbc.queryForObject(
                "SELECT creador_id FROM projects WHERE id = ?", Long.class, event.getProyectoId()
            );

            String payload = String.format(
                "{\"proyectoId\":%d,\"fromState\":\"%s\",\"toState\":\"%s\"}",
                event.getProyectoId(), event.getFromState(), event.getToState()
            );
            notificationService.createNotification(
                creatorId,
                "PROJECT_STATE_CHANGED",
                projectName + " cambió de estado",
                "Tu proyecto pasó de " + event.getFromState() + " a " + event.getToState() + ".",
                payload
            );

            // Send email for meaningful transitions
            boolean sendEmail = false;
            String emailSubject = "";
            String emailDesc = "";
            if ("EN_AUDITORIA".equals(event.getFromState()) && "FINANCIAMIENTO".equals(event.getToState())) {
                sendEmail = true;
                emailSubject = projectName + " aprobado — ya podés financiarlo";
                emailDesc = "Tu proyecto " + projectName + " fue aprobado en auditoría y ya está en etapa de financiamiento.";
            } else if ("FINANCIAMIENTO".equals(event.getFromState()) && "EJECUCION".equals(event.getToState())) {
                sendEmail = true;
                emailSubject = projectName + " alcanzó su meta de financiamiento";
                emailDesc = "Felicitaciones! " + projectName + " completó su financiamiento y ahora está en ejecución.";
            }

            if (sendEmail) {
                String creatorEmail = jdbc.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, creatorId
                );
                String emailBody = String.format("""
                    <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                        <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Novedad de tu proyecto</td></tr>
                        <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:14px;font-weight:600;">%s</td></tr>
                        <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                        <tr><td style="padding:8px 0 0 0;color:#94A3B8;font-size:12px;">%s → %s</td></tr>
                    </table>
                    """, emailDesc, event.getFromState(), event.getToState());
                emailService.sendEmail(creatorEmail, emailSubject, emailBody);
            }

            log.info("ProjectStateChanged processed for proyecto={}, toState={}",
                event.getProyectoId(), event.getToState());
        } catch (Exception e) {
            log.error("Failed to process ProjectStateChangedEvent for proyecto={}: {}",
                event.getProyectoId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectAudited(ProjectAuditedEvent event) {
        try {
            String projectName = jdbc.queryForObject(
                "SELECT titulo FROM projects WHERE id = ?", String.class, event.getProyectoId()
            );

            String resultStr = event.getResult() == ProjectAuditedEvent.Result.APPROVED
                ? "aprobado" : "rechazado";
            String payload = String.format(
                "{\"proyectoId\":%d,\"auditorId\":%d,\"result\":\"%s\",\"findingId\":%d}",
                event.getProyectoId(), event.getAuditorId(),
                event.getResult().name(), event.getFindingId()
            );

            String message = projectName + " ha sido " + resultStr + " en la auditoría.";

            // Look up the project creator for both in-app notification and email
            Long creadorId = jdbc.queryForObject(
                "SELECT creador_id FROM projects WHERE id = ?",
                Long.class, event.getProyectoId()
            );

            notificationService.createNotification(
                creadorId,
                "PROJECT_AUDITED",
                "Proyecto auditado",
                message,
                payload
            );

            String creatorEmail = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, creadorId
            );
            String emailBody = String.format("""
                <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Resultado de auditoría</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:14px;font-weight:600;">%s</td></tr>
                    <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0 0 0;color:#94A3B8;font-size:12px;">Proyecto: %s</td></tr>
                </table>
                """, message, projectName);
            emailService.sendEmail(creatorEmail, "Resultado de auditoría — " + projectName, emailBody);

            log.info("ProjectAudited processed for proyecto={}, result={}",
                event.getProyectoId(), event.getResult());
        } catch (Exception e) {
            log.error("Failed to process ProjectAuditedEvent for proyecto={}: {}",
                event.getProyectoId(), e.getMessage(), e);
        }
    }
}
