package com.systeam.tokenization.dto;

import java.math.BigDecimal;

public class SubtokenPriceResponse {

    private Long proyectoId;
    private BigDecimal precioActual;
    private BigDecimal precioBase;
    private int suministroTotal;
    private int cupoRestante;
    private int vendidos;
    private BigDecimal factorDemanda;
    private BigDecimal factorRendimiento;

    public SubtokenPriceResponse() {}

    public SubtokenPriceResponse(Long proyectoId, BigDecimal precioActual, BigDecimal precioBase,
                                  int suministroTotal, int cupoRestante,
                                  BigDecimal factorDemanda, BigDecimal factorRendimiento) {
        this.proyectoId = proyectoId;
        this.precioActual = precioActual;
        this.precioBase = precioBase;
        this.suministroTotal = suministroTotal;
        this.cupoRestante = cupoRestante;
        this.vendidos = suministroTotal - cupoRestante;
        this.factorDemanda = factorDemanda;
        this.factorRendimiento = factorRendimiento;
    }

    public Long getProyectoId() { return proyectoId; }
    public BigDecimal getPrecioActual() { return precioActual; }
    public BigDecimal getPrecioBase() { return precioBase; }
    public int getSuministroTotal() { return suministroTotal; }
    public int getCupoRestante() { return cupoRestante; }
    public int getVendidos() { return vendidos; }
    public BigDecimal getFactorDemanda() { return factorDemanda; }
    public BigDecimal getFactorRendimiento() { return factorRendimiento; }
}
