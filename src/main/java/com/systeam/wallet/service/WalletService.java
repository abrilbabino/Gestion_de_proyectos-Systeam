package com.systeam.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.notificaciones.event.WalletTransferEvent;
import com.systeam.wallet.dto.TransferTokensRequest;
import com.systeam.wallet.dto.TransferTokensResponse;
import com.systeam.wallet.dto.WalletHistoryItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.dto.WalletSummaryResponse.Balances;
import com.systeam.wallet.dto.WalletSummaryResponse.PortfolioItem;
import com.systeam.wallet.repository.WalletRepository;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final BlockchainService blockchainService;
    private final ApplicationEventPublisher eventPublisher;

    public WalletService(WalletRepository walletRepository, BlockchainService blockchainService,
                         ApplicationEventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.blockchainService = blockchainService;
        this.eventPublisher = eventPublisher;
    }

    public void syncIdeaBalance(Long userId, BigDecimal balance) {
        walletRepository.updateSaldoIdea(userId, balance);
    }

    public void updateWalletAddress(Long userId, String walletAddress) {
        walletRepository.updateWalletAddress(userId, walletAddress);
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

    public List<WalletHistoryItem> getHistory(Long usuarioId, LocalDateTime desde, LocalDateTime hasta) {
        return walletRepository.findHistory(usuarioId, desde, hasta);
    }

    @Transactional
    public TransferTokensResponse transferTokens(Long emisorId, String emailEmisor, TransferTokensRequest request) {
        Long destinatarioId = request.getDestinatarioId();
        BigDecimal cantidad = request.getCantidad();
        String txHash = request.getTxHash();
        String walletEmisor = request.getWalletEmisor();

        if (emisorId.equals(destinatarioId)) {
            throw new ConflictException("No podés transferirte tokens a vos mismo");
        }

        if (!walletRepository.userExists(destinatarioId)) {
            throw new ResourceNotFoundException("El destinatario no existe");
        }

        if (walletRepository.txHashExists(txHash)) {
            throw new ConflictException("La transacción blockchain ya fue registrada");
        }

        try {
            if (!blockchainService.verifyTransaction(txHash)) {
                throw new ConflictException("La transacción blockchain no fue encontrada o falló en Sepolia");
            }
            String senderOnChain = blockchainService.getSenderFromTx(txHash);
            log.info(">>> DEBUG emisorId={} walletEmisor='{}' fromChain='{}'", emisorId, walletEmisor, senderOnChain);
            log.info(">>> DEBUG |walletEmisor|={} |fromChain|={} equalsIgnoreCase={}",
                walletEmisor != null ? walletEmisor.length() : -1,
                senderOnChain != null ? senderOnChain.length() : -1,
                senderOnChain != null && senderOnChain.equalsIgnoreCase(walletEmisor));
            if (senderOnChain == null || !senderOnChain.equalsIgnoreCase(walletEmisor)) {
                throw new ConflictException("La wallet del emisor no coincide con la transacción on-chain");
            }
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verificando tx en blockchain: {}", e.getMessage());
            throw new ConflictException("No se pudo verificar la transacción en la blockchain: " + e.getMessage());
        }

        BigDecimal saldoEmisor = walletRepository.findSaldoIdea(emisorId);
        if (saldoEmisor == null || saldoEmisor.compareTo(cantidad) < 0) {
            throw new ConflictException("Saldo insuficiente para realizar la transferencia");
        }

        walletRepository.debitAndCredit(emisorId, destinatarioId, cantidad);
        TransferTokensResponse response = walletRepository.saveTransfer(emisorId, destinatarioId, cantidad, txHash);

        // Publish event — WalletEventListener handles email dispatch asynchronously
        eventPublisher.publishEvent(new WalletTransferEvent(emisorId, destinatarioId, cantidad, txHash));

        return response;
    }

    public void checkSufficientBalance(Long userId, BigDecimal requiredAmount) {
        BigDecimal currentBalance = walletRepository.findSaldoIdea(userId);
        if (currentBalance == null || currentBalance.compareTo(requiredAmount) < 0) {
            throw new ConflictException("Saldo insuficiente para realizar la operación");
        }
    }

    @Transactional
    public void adjustBalance(Long userId, BigDecimal signedAmount) {
        if (signedAmount.signum() < 0) {
            BigDecimal currentBalance = walletRepository.findSaldoIdea(userId);
            if (currentBalance == null || currentBalance.add(signedAmount).signum() < 0) {
                throw new ConflictException("Saldo insuficiente para realizar la operación");
            }
        }
        walletRepository.adjustSaldoIdea(userId, signedAmount);
    }

    public List<TransferTokensResponse> getTransfers(Long userId) {
        return walletRepository.findTransfersByUser(userId);
    }
}
