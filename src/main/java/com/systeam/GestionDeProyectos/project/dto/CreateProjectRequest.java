package com.systeam.GestionDeProyectos.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    private LocalDateTime plazo;
    private Boolean gobernanzaComunidad;

    @Min(value = 1, message = "El cupo maximo de tokens debe ser al menos 1")
    private Integer cupoMaximoTokens;

    @DecimalMin(value = "0.01", message = "El valor nominal del token debe ser mayor a cero")
    private BigDecimal valorNominalToken;
}
