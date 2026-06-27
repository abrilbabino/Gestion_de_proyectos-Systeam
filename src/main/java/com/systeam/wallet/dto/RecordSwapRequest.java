package com.systeam.wallet.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecordSwapRequest {
    @NotNull(message = "El monto de IDEA no puede ser nulo")
    private BigDecimal amountIdea;

    @NotNull(message = "El monto de USDC no puede ser nulo")
    private BigDecimal amountUsdc;

    @NotBlank(message = "El hash de la transaccion no puede estar vacio")
    private String txHash;
}
