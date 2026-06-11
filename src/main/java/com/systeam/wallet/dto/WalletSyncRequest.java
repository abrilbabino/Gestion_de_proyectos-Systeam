package com.systeam.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSyncRequest {

    @NotNull(message = "El balance no puede ser nulo")
    @Min(value = 0, message = "El balance no puede ser negativo")
    private BigDecimal ideaBalance;
}
