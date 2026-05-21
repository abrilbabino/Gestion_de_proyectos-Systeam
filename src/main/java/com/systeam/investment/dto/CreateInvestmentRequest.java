package com.systeam.investment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInvestmentRequest {

    @NotNull(message = "El ID del proyecto es obligatorio")
    private Long proyectoId;

    @NotNull(message = "El monto en IDEA es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal montoIdea;

    @NotBlank(message = "El hash de la transaccion blockchain es obligatorio")
    private String txHash;
}
