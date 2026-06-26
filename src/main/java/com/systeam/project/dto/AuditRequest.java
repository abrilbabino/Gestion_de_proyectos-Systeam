package com.systeam.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditRequest {
    @NotBlank(message = "El risk score es obligatorio")
    private String riskScore;

    @NotNull(message = "El score de viabilidad es obligatorio")
    @Min(1)
    @Max(10)
    private Integer financialViabilityScore;

    private String observaciones;

    @NotBlank(message = "El dictamen es obligatorio")
    private String dictamen; // APPROVED, REJECTED, NEEDS_CHANGES
}
