package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.WalletTransferEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;
import com.systeam.wallet.repository.WalletRepository;

/**
 * Listens for {@link WalletTransferEvent} after the wallet transfer transaction commits.
 * Creates in-app notifications and sends email to both the sender (emisor) and
 * the recipient (destinatario).
 */
@Component
public class WalletEventListener {

    private static final Logger log = LoggerFactory.getLogger(WalletEventListener.class);

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final WalletRepository walletRepository;

    public WalletEventListener(NotificationService notificationService, EmailService emailService, WalletRepository walletRepository) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.walletRepository = walletRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletTransfer(WalletTransferEvent event) {
        try {
            // Resolve names for richer messages
            String emisorName = walletRepository.findNameById(event.getEmisorId());
            String destinatarioName = walletRepository.findNameById(event.getDestinatarioId());

            String sentPayload = String.format(
                "{\"cantidad\":%s,\"destinatarioId\":%d,\"txHash\":\"%s\"}",
                event.getCantidad().toPlainString(), event.getDestinatarioId(), event.getTxHash()
            );
            notificationService.createNotification(
                event.getEmisorId(),
                "WALLET_TRANSFER_SENT",
                "Transferencia enviada",
                "Has enviado " + event.getCantidad().toPlainString() + " IDEA a " + destinatarioName + ".",
                sentPayload
            );

            String receivedPayload = String.format(
                "{\"cantidad\":%s,\"emisorId\":%d,\"txHash\":\"%s\"}",
                event.getCantidad().toPlainString(), event.getEmisorId(), event.getTxHash()
            );
            notificationService.createNotification(
                event.getDestinatarioId(),
                "WALLET_TRANSFER_RECEIVED",
                "Transferencia recibida",
                "Has recibido " + event.getCantidad().toPlainString() + " IDEA de " + emisorName + ".",
                receivedPayload
            );

            // Email to sender — rich HTML body
            String emisorEmail = walletRepository.findEmailById(event.getEmisorId());
            String emisorBody = String.format("""
                <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Monto enviado</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:20px;font-weight:700;">%s <span style="color:#7C3AED;">$IDEA</span></td></tr>
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Destinatario</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:14px;">%s</td></tr>
                    <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0 0 0;color:#64748B;font-size:11px;">Tx: %s</td></tr>
                </table>
                """, event.getCantidad().toPlainString(), destinatarioName, event.getTxHash());
            emailService.sendEmail(emisorEmail, "Transferencia enviada", emisorBody);

            // Email to recipient — rich HTML body
            String destinatarioEmail = walletRepository.findEmailById(event.getDestinatarioId());
            String destinatarioBody = String.format("""
                <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Monto recibido</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:20px;font-weight:700;">%s <span style="color:#7C3AED;">$IDEA</span></td></tr>
                    <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;">Remitente</td></tr>
                    <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:14px;">%s</td></tr>
                    <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                    <tr><td style="padding:8px 0 0 0;color:#64748B;font-size:11px;">Tx: %s</td></tr>
                </table>
                """, event.getCantidad().toPlainString(), emisorName, event.getTxHash());
            emailService.sendEmail(destinatarioEmail, "Transferencia recibida", destinatarioBody);

            log.info("WalletTransfer processed: {} IDEA from {} to {} (tx={})",
                event.getCantidad(), event.getEmisorId(), event.getDestinatarioId(), event.getTxHash());
        } catch (Exception e) {
            log.error("Failed to process WalletTransferEvent from {} to {}: {}",
                event.getEmisorId(), event.getDestinatarioId(), e.getMessage(), e);
        }
    }
}
