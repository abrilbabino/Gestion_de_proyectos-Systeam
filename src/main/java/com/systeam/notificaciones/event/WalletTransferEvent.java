package com.systeam.notificaciones.event;

import java.math.BigDecimal;

/**
 * Published after an on-chain wallet transfer between two users is recorded.
 */
public class WalletTransferEvent {

    private final Long emisorId;
    private final Long destinatarioId;
    private final BigDecimal cantidad;
    private final String txHash;

    public WalletTransferEvent(Long emisorId, Long destinatarioId, BigDecimal cantidad, String txHash) {
        this.emisorId       = emisorId;
        this.destinatarioId = destinatarioId;
        this.cantidad       = cantidad;
        this.txHash         = txHash;
    }

    public Long getEmisorId()       { return emisorId; }
    public Long getDestinatarioId() { return destinatarioId; }
    public BigDecimal getCantidad() { return cantidad; }
    public String getTxHash()       { return txHash; }
}
