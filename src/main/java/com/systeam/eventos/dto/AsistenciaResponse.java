package com.systeam.eventos.dto;

import java.time.LocalDateTime;

public class AsistenciaResponse {

    private Long id;
    private Long eventoId;
    private Long userId;
    private LocalDateTime confirmedAt;

    public AsistenciaResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventoId() { return eventoId; }
    public void setEventoId(Long eventoId) { this.eventoId = eventoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
