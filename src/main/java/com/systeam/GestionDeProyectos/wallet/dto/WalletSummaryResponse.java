package com.systeam.GestionDeProyectos.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletSummaryResponse {
    private Balances balances;
    private List<PortfolioItem> portfolio;

    @Data
    @Builder
    public static class Balances {
        private BigDecimal idea;
        private BigDecimal usdt;
    }

    @Data
    @Builder
    public static class PortfolioItem {
        private String subtoken;
        private Integer cantidad;
        private BigDecimal precioActual;
    }
}
