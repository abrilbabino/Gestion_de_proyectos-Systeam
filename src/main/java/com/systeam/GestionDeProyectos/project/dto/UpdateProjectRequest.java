package com.systeam.GestionDeProyectos.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Size(max = 200, message = "El titulo no puede superar los 200 caracteres")
    private String titulo;

    @Size(max = 8000, message = "La descripcion no puede superar los 8000 caracteres")
    private String descripcion;

    @DecimalMin(value = "0.01", message = "El monto requerido debe ser mayor a cero")
    private BigDecimal montoRequerido;

    private LocalDateTime plazo;
    private Boolean gobernanzaComunidad;
}
