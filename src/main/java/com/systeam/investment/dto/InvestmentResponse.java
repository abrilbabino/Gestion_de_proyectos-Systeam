package com.systeam.investment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvestmentResponse {
    private Long id;
    private Long usuarioId;
    private Long proyectoId;
    private String proyectoTitulo;
    private String proyectoEstado;
    private BigDecimal montoIdea;
    private Integer subTokensRecibidos;
    private String txHash;
    private String estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
