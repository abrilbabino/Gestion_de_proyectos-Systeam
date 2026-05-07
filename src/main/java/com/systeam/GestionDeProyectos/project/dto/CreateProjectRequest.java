package com.systeam.GestionDeProyectos.project.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 200, message = "El titulo no puede superar los 200 caracteres")
    private String title;

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 8000, message = "La descripcion no puede superar los 8000 caracteres")
    private String description;

    @NotBlank(message = "El objetivo es obligatorio")
    @Size(max = 3000, message = "El objetivo no puede superar los 3000 caracteres")
    private String objective;

    @NotNull(message = "El monto requerido es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto requerido debe ser mayor a cero")
    private BigDecimal requiredAmount;

    private Long cantidadDeTokens;
    private BigDecimal valorNominal;
    
    private Boolean gobernanzaComunidad; 
}