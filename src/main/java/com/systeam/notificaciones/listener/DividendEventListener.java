package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.DividendDistributedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for {@link DividendDistributedEvent} after the dividend distribution
 * transaction commits. Creates an in-app notification and sends an email to the
 * beneficiary.
 */
@Component
public class DividendEventListener {

    private static final Logger log = LoggerFactory.getLogger(DividendEventListener.class);

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    public DividendEventListener(NotificationService notificationService, EmailService emailService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDividendDistributed(DividendDistributedEvent event) {
        try {
            String projectName = jdbc.queryForObject(
                "SELECT titulo FROM projects WHERE id = ?", String.class, event.getProyectoId()
            );

            String payload = String.format(
                "{\"proyectoId\":%d,\"monto\":%s}",
                event.getProyectoId(), event.getMonto().toPlainString()
            );
            notificationService.createNotification(
                event.getBeneficiarioId(),
                "DIVIDEND_DISTRIBUTED",
                "Dividendos acreditados",
                "Recibiste " + event.getMonto().toPlainString() + " IDEA en dividendos de " + projectName + ".",
                payload
            );

            String email = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, event.getBeneficiarioId()
            );
            String body = String.format("""
                <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Dividendos acreditados</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:20px;font-weight:700;">%s <span style="color:#7C3AED;">$IDEA</span></td></tr>
                    <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0 0 0;color:#94A3B8;font-size:12px;">Proyecto: %s</td></tr>
                </table>
                """, event.getMonto().toPlainString(), projectName);
            emailService.sendEmail(email, "Dividendos de " + projectName, body);

            log.info("DividendDistributed processed for beneficiario={}, proyecto={}",
                event.getBeneficiarioId(), event.getProyectoId());
        } catch (Exception e) {
            log.error("Failed to process DividendDistributedEvent for beneficiario={}: {}",
                event.getBeneficiarioId(), e.getMessage(), e);
        }
    }
}
