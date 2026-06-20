package com.systeam.notificaciones.listener;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.MarketplaceEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for {@link MarketplaceEvent} after the marketplace transaction commits.
 * Creates in-app notifications for all types (LISTED, SOLD, CANCELLED).
 * Sends email only on SOLD to both buyer and seller.
 */
@Component
public class MarketplaceEventListener {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceEventListener.class);
    private static final BigDecimal WEI_DIVISOR = new BigDecimal("1000000000000000000");

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    public MarketplaceEventListener(NotificationService notificationService, EmailService emailService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketplaceEvent(MarketplaceEvent event) {
        try {
            switch (event.getType()) {
                case LISTED:
                    handleListed(event);
                    break;
                case SOLD:
                    handleSold(event);
                    break;
                case CANCELLED:
                    handleCancelled(event);
                    break;
            }
            log.info("MarketplaceEvent processed for projectId={}, type={}",
                event.getProjectId(), event.getType());
        } catch (Exception e) {
            log.error("Failed to process MarketplaceEvent for projectId={}: {}",
                event.getProjectId(), e.getMessage(), e);
        }
    }

    private void handleListed(MarketplaceEvent event) {
        String projectName = event.getProjectName() != null ? event.getProjectName() : "ID " + event.getProjectId();
        String cantidad = event.getCantidad() != null ? event.getCantidad().toString() : "—";
        String payload = String.format(
            "{\"projectId\":%d,\"sellerId\":%d,\"projectName\":\"%s\",\"cantidad\":\"%s\",\"type\":\"%s\"}",
            event.getProjectId(), event.getSellerId(), projectName, cantidad, event.getType().name()
        );
        notificationService.createNotification(
            event.getSellerId(),
            "MARKETPLACE_LISTED",
            "Listado en marketplace",
            "Tu proyecto " + projectName + " ha sido listado en el marketplace.",
            payload
        );
    }

    private void handleSold(MarketplaceEvent event) {
        String projectName  = event.getProjectName() != null ? event.getProjectName() : "ID " + event.getProjectId();
        String cantidadStr  = event.getCantidad() != null ? event.getCantidad().toString() : "—";
        String precioWei    = formatWei(event.getPrecioUnitario());
        String totalWei     = calcTotalWei(event.getPrecioUnitario(), event.getCantidad());
        String txHash       = event.getTxHash() != null ? event.getTxHash() : "—";
        String txShort      = txHash.length() > 20 ? txHash.substring(0, 10) + "…" + txHash.substring(txHash.length() - 8) : txHash;

        String payload = String.format(
            "{\"projectId\":%d,\"buyerId\":%d,\"sellerId\":%d,\"projectName\":\"%s\",\"cantidad\":\"%s\",\"precioUnitario\":\"%s\",\"total\":\"%s\",\"txHash\":\"%s\",\"type\":\"%s\"}",
            event.getProjectId(), event.getBuyerId(), event.getSellerId(),
            projectName, cantidadStr, precioWei, totalWei, txHash, event.getType().name()
        );

        // In-app to buyer
        notificationService.createNotification(
            event.getBuyerId(),
            "MARKETPLACE_SOLD",
            "Compra en marketplace",
            "Has comprado " + cantidadStr + " tokens de " + projectName + " por " + totalWei + " $IDEA.",
            payload
        );

        // In-app to seller
        notificationService.createNotification(
            event.getSellerId(),
            "MARKETPLACE_SOLD",
            "Venta en marketplace",
            "Has vendido " + cantidadStr + " tokens de " + projectName + " por " + totalWei + " $IDEA.",
            payload
        );

        // Email to buyer
        String buyerEmail = jdbc.queryForObject(
            "SELECT email FROM users WHERE id = ?", String.class, event.getBuyerId()
        );
        String buyerBody = buildSoldEmailBody(projectName, cantidadStr, precioWei, totalWei, txShort, false);
        emailService.sendEmail(buyerEmail, "Compra en marketplace — " + projectName, buyerBody);

        // Email to seller
        String sellerEmail = jdbc.queryForObject(
            "SELECT email FROM users WHERE id = ?", String.class, event.getSellerId()
        );
        String sellerBody = buildSoldEmailBody(projectName, cantidadStr, precioWei, totalWei, txShort, true);
        emailService.sendEmail(sellerEmail, "Venta en marketplace — " + projectName, sellerBody);
    }

    private void handleCancelled(MarketplaceEvent event) {
        String projectName = event.getProjectName() != null ? event.getProjectName() : "ID " + event.getProjectId();
        String payload = String.format(
            "{\"projectId\":%d,\"sellerId\":%d,\"projectName\":\"%s\",\"type\":\"%s\"}",
            event.getProjectId(), event.getSellerId(), projectName, event.getType().name()
        );
        notificationService.createNotification(
            event.getSellerId(),
            "MARKETPLACE_CANCELLED",
            "Listado cancelado",
            "El listado de " + projectName + " ha sido cancelado.",
            payload
        );
    }

    // ── Helpers ──

    /** Formats a wei BigInteger to a human-readable $IDEA string (e.g. "12.5000"). */
    private String formatWei(BigInteger wei) {
        if (wei == null) return "—";
        return new BigDecimal(wei).divide(WEI_DIVISOR, 4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Returns the total = precioUnitario * cantidad, formatted in $IDEA. */
    private String calcTotalWei(BigInteger precioUnitario, BigInteger cantidad) {
        if (precioUnitario == null || cantidad == null) return "—";
        return formatWei(precioUnitario.multiply(cantidad));
    }

    private String buildSoldEmailBody(String projectName, String cantidad, String precio,
                                       String total, String txShort, boolean isSeller) {
        String action = isSeller ? "vendido" : "comprado";
        return String.format("""
            <table style="width:100%%;background-color:#1A1D35;border-radius:8px;padding:16px;margin-bottom:16px;">
                <tr><td style="padding:8px 0;color:#94A3B8;font-size:12px;text-transform:uppercase;letter-spacing:0.5px;">
                    %s en marketplace
                </td></tr>
                <tr><td style="padding:0 0 12px 0;color:#F1F5F9;font-size:14px;font-weight:600;">
                    Has %s <span style="color:#A78BFA;">%s tokens</span> de <span style="color:#F1F5F9;">%s</span>
                </td></tr>
                <tr><td style="border-top:1px solid #1E2240;"></td></tr>
                <tr><td style="padding:12px 0 0 0;">
                    <table style="width:100%%;">
                        <tr>
                            <td style="color:#94A3B8;font-size:12px;padding:4px 0;">Proyecto</td>
                            <td style="color:#F1F5F9;font-size:13px;text-align:right;padding:4px 0;font-weight:500;">%s</td>
                        </tr>
                        <tr>
                            <td style="color:#94A3B8;font-size:12px;padding:4px 0;">Cantidad</td>
                            <td style="color:#F1F5F9;font-size:13px;text-align:right;padding:4px 0;font-weight:500;">%s tokens</td>
                        </tr>
                        <tr>
                            <td style="color:#94A3B8;font-size:12px;padding:4px 0;">Precio unitario</td>
                            <td style="color:#F1F5F9;font-size:13px;text-align:right;padding:4px 0;font-weight:500;">%s $IDEA</td>
                        </tr>
                        <tr>
                            <td style="color:#94A3B8;font-size:12px;padding:4px 0;">Total</td>
                            <td style="color:#A78BFA;font-size:14px;text-align:right;padding:4px 0;font-weight:700;">%s $IDEA</td>
                        </tr>
                        <tr>
                            <td style="color:#94A3B8;font-size:12px;padding:4px 0;">Transacción</td>
                            <td style="color:#64748B;font-size:11px;text-align:right;padding:4px 0;font-family:monospace;">%s</td>
                        </tr>
                    </table>
                </td></tr>
            </table>
            """,
            isSeller ? "Venta" : "Compra",
            action, cantidad, projectName,
            projectName, cantidad, precio, total, txShort
        );
    }
}
