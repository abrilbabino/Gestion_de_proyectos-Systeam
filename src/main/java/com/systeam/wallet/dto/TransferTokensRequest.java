package com.systeam.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class TransferTokensRequest {

    @NotNull(message = "El destinatario es obligatorio")
    private Long destinatarioId;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero")
    private BigDecimal cantidad;

    @NotBlank(message = "El hash de la transacción es obligatorio")
    @Pattern(regexp = "^0x[a-fA-F0-9]{64}$", message = "El txHash no es válido")
    private String txHash;

    @NotBlank(message = "La wallet del emisor es obligatoria")
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "La wallet del emisor no es válida")
    private String walletEmisor;
}
