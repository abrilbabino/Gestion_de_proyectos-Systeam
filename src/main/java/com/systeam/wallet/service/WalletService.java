package com.systeam.wallet.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.dto.WalletSummaryResponse.Balances;
import com.systeam.wallet.dto.WalletSummaryResponse.PortfolioItem;
import com.systeam.wallet.repository.WalletRepository;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public void syncIdeaBalance(Long userId, BigDecimal balance) {
        walletRepository.updateSaldoIdea(userId, balance);
    }

    public WalletSummaryResponse getSummary(Long userId) {
        BigDecimal idea = walletRepository.findSaldoIdea(userId);
        BigDecimal usdt = walletRepository.findSaldoUsdt(userId);

        List<Object[]> rows = walletRepository.findPortfolio(userId);
        List<PortfolioItem> portfolio = new ArrayList<>();
        for (Object[] row : rows) {
            portfolio.add(PortfolioItem.builder()
                    .subtokenId((Long) row[0])
                    .proyectoNombre((String) row[1])
                    .subtokenNombre((String) row[2])
                    .subtokenSimbolo((String) row[3])
                    .cantidad((Integer) row[4])
                    .precioActual((BigDecimal) row[5])
                    .contractAddress((String) row[6])
                    .proyectoEstado((String) row[7])
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
