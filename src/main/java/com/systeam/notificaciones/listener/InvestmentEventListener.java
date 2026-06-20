package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.InvestmentConfirmedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for {@link InvestmentConfirmedEvent} after the investment transaction commits.
 * Creates an in-app notification and sends a confirmation email to the investor.
 */
@Component
public class InvestmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(InvestmentEventListener.class);

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    public InvestmentEventListener(NotificationService notificationService, EmailService emailService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvestmentConfirmed(InvestmentConfirmedEvent event) {
        try {
            String projectName = jdbc.queryForObject(
                "SELECT titulo FROM projects WHERE id = ?", String.class, event.getProyectoId()
            );

            String payload = String.format(
                "{\"proyectoId\":%d,\"monto\":%s,\"cantidadSubTokens\":%d,\"txHash\":\"%s\"}",
                event.getProyectoId(), event.getMonto().toPlainString(),
                event.getCantidadSubTokens(), event.getTxHash()
            );
            String message = String.format(
                "Invertiste %s $IDEA en %s — recibiste %d tokens.",
                event.getMonto().toPlainString(), projectName, event.getCantidadSubTokens()
            );
            notificationService.createNotification(
                event.getInversorId(),
                "INVESTMENT_CONFIRMED",
                "Inversión confirmada",
                message,
                payload
            );

            String email = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, event.getInversorId()
            );
            String body = String.format("""
                <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Inversión confirmada</td></tr>
                    <tr><td style="padding:0;color:#F1F5F9;font-size:16px;font-weight:600;">%s</td></tr>
                    <tr><td style="padding:12px 0;border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Monto invertido</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:20px;font-weight:700;">%s <span style="color:#7C3AED;">$IDEA</span></td></tr>
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Tokens recibidos</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:18px;font-weight:600;">%d</td></tr>
                    <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0 0 0;color:#64748B;font-size:11px;">Tx: %s</td></tr>
                </table>
                """, projectName, event.getMonto().toPlainString(),
                event.getCantidadSubTokens(), event.getTxHash());
            emailService.sendEmail(email, "Inversión en " + projectName + " confirmada", body);

            log.info("InvestmentConfirmed processed for inversor={}, proyecto={}, tokens={}",
                event.getInversorId(), event.getProyectoId(), event.getCantidadSubTokens());
        } catch (Exception e) {
            log.error("Failed to process InvestmentConfirmedEvent for inversor={}: {}",
                event.getInversorId(), e.getMessage(), e);
        }
    }
}
