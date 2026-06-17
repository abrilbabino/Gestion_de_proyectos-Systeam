package com.systeam.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateWalletAddressRequest {

    @NotBlank(message = "La wallet address es obligatoria")
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "La wallet address no es válida")
    private String walletAddress;
}
