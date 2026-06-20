package com.systeam.notificaciones.event;

import java.math.BigDecimal;

/**
 * Published after an investment is confirmed on-chain and the transaction commits.
 * All IDs are primitives; listener re-queries if full entity state is needed.
 */
public class InvestmentConfirmedEvent {

    private final Long inversorId;
    private final Long proyectoId;
    private final BigDecimal monto;
    private final int cantidadSubTokens;
    private final String txHash;

    public InvestmentConfirmedEvent(Long inversorId, Long proyectoId, BigDecimal monto, int cantidadSubTokens, String txHash) {
        this.inversorId = inversorId;
        this.proyectoId = proyectoId;
        this.monto       = monto;
        this.cantidadSubTokens = cantidadSubTokens;
        this.txHash      = txHash;
    }

    public Long getInversorId() { return inversorId; }
    public Long getProyectoId() { return proyectoId; }
    public BigDecimal getMonto()   { return monto; }
    public int getCantidadSubTokens() { return cantidadSubTokens; }
    public String getTxHash()      { return txHash; }
}
