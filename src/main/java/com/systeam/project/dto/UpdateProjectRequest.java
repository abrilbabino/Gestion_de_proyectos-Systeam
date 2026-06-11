package com.systeam.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Size(max = 200, message = "El titulo no puede superar los 200 caracteres")
    private String titulo;

    @Size(max = 8000, message = "La descripcion no puede superar los 8000 caracteres")
    private String descripcion;

    @DecimalMin(value = "0.01", message = "El monto requerido debe ser mayor a cero")
    private BigDecimal montoRequerido;

    @Future(message = "El plazo debe ser una fecha futura")
    private LocalDateTime plazo;
    private Boolean gobernanzaComunidad;

    @Min(value = 1, message = "El cupo maximo de tokens debe ser al menos 1")
    private Integer cupoMaximoTokens;

    @DecimalMin(value = "0.01", message = "El valor nominal del token debe ser mayor a cero")
    private BigDecimal valorNominalToken;

    @Size(min = 2, max = 5, message = "El simbolo debe tener entre 2 y 5 caracteres")
    private String simbolo;
}
