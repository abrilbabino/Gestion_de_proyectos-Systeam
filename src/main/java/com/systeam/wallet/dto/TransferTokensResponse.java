package com.systeam.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferTokensResponse {
    private Long id;
    private Long emisorId;
    private Long destinatarioId;
    private BigDecimal cantidad;
    private String txHash;
    private LocalDateTime fecha;
}
