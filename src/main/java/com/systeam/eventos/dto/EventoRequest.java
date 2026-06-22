package com.systeam.eventos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EventoRequest {

    @NotBlank
    private String titulo;

    private String descripcion;

    @NotNull
    private LocalDateTime fechaEvento;

    @NotNull
    private BigDecimal rewardAmount;

    private Long proyectoId;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }
    public BigDecimal getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(BigDecimal rewardAmount) { this.rewardAmount = rewardAmount; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
}
