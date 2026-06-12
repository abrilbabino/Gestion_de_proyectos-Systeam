package com.systeam.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletHistoryItem {
    private String tipo;
    private BigDecimal monto;
    private Long cantidad;
    private String txHash;
    private String descripcion;
    private LocalDateTime fecha;
}
