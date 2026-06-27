package com.systeam.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HitoReleaseRequest {
    
    private String comprobanteUrl;

    @NotBlank(message = "La dirección del Escrow es obligatoria")
    private String escrowAddress;
}
