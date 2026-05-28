package com.systeam.marketplace.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;

public class ListingResponse {

    private Long id;
    private BigInteger onChainId;
    private Long sellerId;
    private String sellerName;
    private Long subtokenId;
    private String projectName;
    private BigInteger cantidad;
    private BigInteger cantidadInicial;
    private BigInteger precioUnitario;
    private BigInteger precioTotal;
    private String estado;
    private String txHash;
    private LocalDateTime createdAt;

    public ListingResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigInteger getOnChainId() { return onChainId; }
    public void setOnChainId(BigInteger onChainId) { this.onChainId = onChainId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public Long getSubtokenId() { return subtokenId; }
    public void setSubtokenId(Long subtokenId) { this.subtokenId = subtokenId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public BigInteger getCantidad() { return cantidad; }
    public void setCantidad(BigInteger cantidad) { this.cantidad = cantidad; }
    public BigInteger getCantidadInicial() { return cantidadInicial; }
    public void setCantidadInicial(BigInteger cantidadInicial) { this.cantidadInicial = cantidadInicial; }
    public BigInteger getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigInteger precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigInteger getPrecioTotal() { return precioTotal; }
    public void setPrecioTotal(BigInteger precioTotal) { this.precioTotal = precioTotal; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
