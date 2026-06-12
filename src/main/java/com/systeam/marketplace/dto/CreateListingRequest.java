package com.systeam.marketplace.dto;

import java.math.BigInteger;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateListingRequest {

    @NotNull
    private Long subtokenId;

    @NotNull @Positive
    private BigInteger cantidad;

    @NotNull @Positive
    private BigInteger precioUnitario;

    @NotNull
    private String txHash;

    public Long getSubtokenId() { return subtokenId; }
    public void setSubtokenId(Long subtokenId) { this.subtokenId = subtokenId; }
    public BigInteger getCantidad() { return cantidad; }
    public void setCantidad(BigInteger cantidad) { this.cantidad = cantidad; }
    public BigInteger getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigInteger precioUnitario) { this.precioUnitario = precioUnitario; }
    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
}
