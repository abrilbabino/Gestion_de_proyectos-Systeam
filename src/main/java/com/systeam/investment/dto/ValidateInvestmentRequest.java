package com.systeam.investment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ValidateInvestmentRequest {

    @NotNull(message = "El ID del proyecto es obligatorio")
    private Long proyectoId;

    @NotNull(message = "El monto en IDEA es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal montoIdea;
}
