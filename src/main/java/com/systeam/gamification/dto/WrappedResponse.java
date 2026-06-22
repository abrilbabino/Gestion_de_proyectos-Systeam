package com.systeam.gamification.dto;

import java.math.BigDecimal;

public class WrappedResponse {
    private BigDecimal totalInvertido;
    private int proyectosFondeados;
    private String rubroFavorito;
    private BigDecimal roiPorcentaje;
    private String nivel;
    private Integer rankingPosicion;
    private Integer rankingTotalUsuarios;
    private int transaccionesMarketplace;
    private BigDecimal volumenMarketplace;
    private java.util.List<String> topProyectosRubro = new java.util.ArrayList<>();
    private String proyectoMasInvertido;
    private BigDecimal montoMasInvertido = BigDecimal.ZERO;

    public WrappedResponse() {}

    public WrappedResponse(BigDecimal totalInvertido, int proyectosFondeados, String rubroFavorito,
                           BigDecimal roiPorcentaje, String nivel, Integer rankingPosicion, Integer rankingTotalUsuarios,
                           int transaccionesMarketplace, BigDecimal volumenMarketplace) {
        this.totalInvertido = totalInvertido;
        this.proyectosFondeados = proyectosFondeados;
        this.rubroFavorito = rubroFavorito;
        this.roiPorcentaje = roiPorcentaje;
        this.nivel = nivel;
        this.rankingPosicion = rankingPosicion;
        this.rankingTotalUsuarios = rankingTotalUsuarios;
        this.transaccionesMarketplace = transaccionesMarketplace;
        this.volumenMarketplace = volumenMarketplace;
    }

    public BigDecimal getTotalInvertido() {
        return totalInvertido;
    }

    public void setTotalInvertido(BigDecimal totalInvertido) {
        this.totalInvertido = totalInvertido;
    }

    public int getProyectosFondeados() {
        return proyectosFondeados;
    }

    public void setProyectosFondeados(int proyectosFondeados) {
        this.proyectosFondeados = proyectosFondeados;
    }

    public String getRubroFavorito() {
        return rubroFavorito;
    }

    public void setRubroFavorito(String rubroFavorito) {
        this.rubroFavorito = rubroFavorito;
    }

    public BigDecimal getRoiPorcentaje() {
        return roiPorcentaje;
    }

    public void setRoiPorcentaje(BigDecimal roiPorcentaje) {
        this.roiPorcentaje = roiPorcentaje;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public Integer getRankingPosicion() {
        return rankingPosicion;
    }

    public void setRankingPosicion(Integer rankingPosicion) {
        this.rankingPosicion = rankingPosicion;
    }

    public Integer getRankingTotalUsuarios() {
        return rankingTotalUsuarios;
    }

    public void setRankingTotalUsuarios(Integer rankingTotalUsuarios) {
        this.rankingTotalUsuarios = rankingTotalUsuarios;
    }

    public int getTransaccionesMarketplace() {
        return transaccionesMarketplace;
    }

    public void setTransaccionesMarketplace(int transaccionesMarketplace) {
        this.transaccionesMarketplace = transaccionesMarketplace;
    }

    public BigDecimal getVolumenMarketplace() {
        return volumenMarketplace;
    }

    public void setVolumenMarketplace(BigDecimal volumenMarketplace) {
        this.volumenMarketplace = volumenMarketplace;
    }

    public java.util.List<String> getTopProyectosRubro() {
        return topProyectosRubro;
    }

    public void setTopProyectosRubro(java.util.List<String> topProyectosRubro) {
        this.topProyectosRubro = topProyectosRubro;
    }

    public String getProyectoMasInvertido() {
        return proyectoMasInvertido;
    }

    public void setProyectoMasInvertido(String proyectoMasInvertido) {
        this.proyectoMasInvertido = proyectoMasInvertido;
    }

    public BigDecimal getMontoMasInvertido() {
        return montoMasInvertido;
    }

    public void setMontoMasInvertido(BigDecimal montoMasInvertido) {
        this.montoMasInvertido = montoMasInvertido;
    }
}
