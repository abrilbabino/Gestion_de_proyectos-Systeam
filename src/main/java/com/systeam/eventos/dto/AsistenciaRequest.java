package com.systeam.eventos.dto;

import jakarta.validation.constraints.NotNull;

public class AsistenciaRequest {

    @NotNull
    private Long userId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
