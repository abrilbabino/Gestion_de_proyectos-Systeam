package com.systeam.notificaciones.event;

import java.math.BigInteger;

/**
 * Published after a marketplace action (list, buy, cancel) on a project share.
 */
public class MarketplaceEvent {

    public enum Type {
        LISTED,
        SOLD,
        CANCELLED
    }

    private final Long projectId;
    private final Long buyerId;
    private final Long sellerId;
    private final Type type;

    // Enriched fields
    private final String projectName;
    private final BigInteger cantidad;
    private final BigInteger precioUnitario;
    private final String txHash;

    /** Full constructor — all fields. */
    public MarketplaceEvent(Long projectId, Long buyerId, Long sellerId, Type type,
                            String projectName, BigInteger cantidad,
                            BigInteger precioUnitario, String txHash) {
        this.projectId      = projectId;
        this.buyerId        = buyerId;
        this.sellerId       = sellerId;
        this.type           = type;
        this.projectName    = projectName;
        this.cantidad       = cantidad;
        this.precioUnitario = precioUnitario;
        this.txHash         = txHash;
    }

    // ── Getters ──

    public Long getProjectId()      { return projectId; }
    public Long getBuyerId()        { return buyerId; }
    public Long getSellerId()       { return sellerId; }
    public Type getType()           { return type; }
    public String getProjectName()  { return projectName; }
    public BigInteger getCantidad() { return cantidad; }
    public BigInteger getPrecioUnitario() { return precioUnitario; }
    public String getTxHash()       { return txHash; }
}
