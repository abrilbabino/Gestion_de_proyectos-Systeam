package com.systeam.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String titulo;
    private String descripcion;
    private BigDecimal montoRequerido;
    private LocalDateTime plazo;
    private String estado;
    private Boolean gobernanzaComunidad;
    private Integer cupoMaximoTokens;
    private BigDecimal valorNominalToken;
    private BigDecimal montoRecaudado;
    private String simbolo;
    private Long creadorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean esDestacado;
    private LocalDateTime fechaBoost;
    private BigDecimal montoBoost;
    private Integer rubro;
}
