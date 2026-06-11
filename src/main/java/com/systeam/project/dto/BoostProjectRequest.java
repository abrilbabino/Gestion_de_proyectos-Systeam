package com.systeam.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BoostProjectRequest {
    @NotBlank(message = "El txHash es requerido")
    private String txHash;
}
