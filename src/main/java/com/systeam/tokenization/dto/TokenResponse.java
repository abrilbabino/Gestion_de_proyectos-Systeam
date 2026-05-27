package com.systeam.tokenization.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {
    private Long id;
    private Long proyectoId;
    private String proyectoTitulo;
    private String nombre;
    private String simbolo;
    private Integer suministroTotal;
    private Integer cupoRestante;
    private BigDecimal precioActual;
    private BigDecimal precioBase;
    private BigDecimal factorVolatilidad;
    private String contractAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
