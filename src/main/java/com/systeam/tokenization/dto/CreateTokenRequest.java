package com.systeam.tokenization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateTokenRequest {

    @NotNull(message = "El ID del proyecto es obligatorio")
    private Long proyectoId;

    @NotNull(message = "El cupo maximo de tokens es obligatorio")
    @Positive(message = "El cupo debe ser mayor a cero")
    private Integer cupoMaximoTokens;

    @NotNull(message = "El valor nominal del token es obligatorio")
    @Positive(message = "El valor nominal debe ser mayor a cero")
    private java.math.BigDecimal valorNominal;
}
