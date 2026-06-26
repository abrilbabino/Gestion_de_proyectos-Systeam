package com.systeam.rewards.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.rewards.repository.RewardLedgerRepository;
import com.systeam.wallet.service.WalletService;

@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    private final RewardLedgerRepository rewardLedgerRepository;
    private final WalletService walletService;

    public RewardService(RewardLedgerRepository rewardLedgerRepository, WalletService walletService) {
        this.rewardLedgerRepository = rewardLedgerRepository;
        this.walletService = walletService;
    }

    /**
     * Idempotent reward accrual. Inserts a ledger row and credits the user's wallet.
     * Returns false if the reward was already ledgered (no double-credit).
     */
    public boolean accrue(Long userId, String reason, String refType,
                          Long refId, String txHash, BigDecimal amount) {
        // Insert into ledger first — ON CONFLICT DO NOTHING is the atomic idempotency guard.
        // This prevents double-spend: only the request that wins the insert proceeds to mint.
        boolean inserted = rewardLedgerRepository.insertIfAbsent(
                userId, reason, refType, refId, txHash, amount);

        if (!inserted) {
            log.info("Reward already ledgered for user={} reason={} refType={} refId={}",
                    userId, reason, refType, refId);
            return false;
        }

        // Safe to mint/credit now — we hold the ledger row exclusively
        if (txHash == null) {
            String resolvedTxHash = walletService.mintIdeaReward(userId, amount);
            if (resolvedTxHash != null) {
                rewardLedgerRepository.updateTxHash(userId, reason, refType, refId, resolvedTxHash);
            }
        }

        log.info("Reward accrued: user={} reason={} refType={} refId={} amount={}",
                userId, reason, refType, refId, amount);
        return true;
    }
}
