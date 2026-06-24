package com.systeam.eventos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EventoResponse {

    private Long id;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaEvento;
    private BigDecimal rewardAmount;
    private Long proyectoId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String cronograma;

    public EventoResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCronograma() { return cronograma; }
    public void setCronograma(String cronograma) { this.cronograma = cronograma; }
}
