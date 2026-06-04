package com.systeam.blockchain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class OracleBillingReportRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal montoFacturado;

    public BigDecimal getMontoFacturado() { return montoFacturado; }
    public void setMontoFacturado(BigDecimal montoFacturado) { this.montoFacturado = montoFacturado; }
}
