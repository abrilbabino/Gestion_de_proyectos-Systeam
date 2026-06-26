package com.systeam.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublishProjectRequest {
    @NotBlank
    private String signature;
    @NotBlank
    private String walletAddress;
}
