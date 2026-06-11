package com.systeam.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 200, message = "El titulo no puede superar los 200 caracteres")
    private String titulo;

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 8000, message = "La descripcion no puede superar los 8000 caracteres")
    private String descripcion;

    @NotNull(message = "El monto requerido es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto requerido debe ser mayor a cero")
    private BigDecimal montoRequerido;

    @NotNull(message = "El plazo es obligatorio")
    @Future(message = "El plazo debe ser una fecha futura")
    private LocalDateTime plazo;

    private Boolean gobernanzaComunidad;

    @NotNull(message = "El cupo maximo de tokens es obligatorio")
    @Min(value = 1, message = "El cupo maximo de tokens debe ser al menos 1")
    private Integer cupoMaximoTokens;

    @NotNull(message = "El valor nominal del token es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor nominal del token debe ser mayor a cero")
    private BigDecimal valorNominalToken;

    @NotBlank(message = "El simbolo del token es obligatorio")
    @Size(min = 2, max = 5, message = "El simbolo debe tener entre 2 y 5 caracteres")
    private String simbolo;
}
