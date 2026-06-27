package com.systeam.project.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Future;

public record HitoRequest(
        @NotBlank(message = "El título del hito no puede estar vacío")
        String titulo,

        @NotNull(message = "El porcentaje es requerido")
        @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
        @DecimalMax(value = "60.00", message = "El porcentaje no puede ser mayor a 60")
        BigDecimal porcentaje,

        @NotNull(message = "El plazo es requerido")
        @Future(message = "El plazo del hito debe ser una fecha futura")
        LocalDateTime plazo
) {}
