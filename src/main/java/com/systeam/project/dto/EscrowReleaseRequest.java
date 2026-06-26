package com.systeam.project.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class EscrowReleaseRequest {
    @NotNull
    private BigDecimal amountToRelease;
    @NotNull
    private String escrowAddress;
}
