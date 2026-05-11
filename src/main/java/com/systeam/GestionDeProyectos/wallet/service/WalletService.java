package com.systeam.GestionDeProyectos.wallet.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.systeam.GestionDeProyectos.wallet.dto.WalletSummaryResponse;
import com.systeam.GestionDeProyectos.wallet.dto.WalletSummaryResponse.Balances;
import com.systeam.GestionDeProyectos.wallet.dto.WalletSummaryResponse.PortfolioItem;
import com.systeam.GestionDeProyectos.wallet.repository.WalletRepository;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public WalletSummaryResponse getSummary(Long userId) {
        BigDecimal idea = walletRepository.findSaldoIdea(userId);
        BigDecimal usdt = walletRepository.findSaldoUsdt(userId);

        List<Object[]> rows = walletRepository.findPortfolio(userId);
        List<PortfolioItem> portfolio = new ArrayList<>();
        for (Object[] row : rows) {
            portfolio.add(PortfolioItem.builder()
                    .subtoken((String) row[0])
                    .cantidad((Integer) row[1])
                    .precioActual((BigDecimal) row[2])
                    .build());
        }

        return WalletSummaryResponse.builder()
                .balances(Balances.builder()
                        .idea(idea != null ? idea : BigDecimal.ZERO)
                        .usdt(usdt != null ? usdt : BigDecimal.ZERO)
                        .build())
                .portfolio(portfolio)
                .build();
    }
}
