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
        // Check idempotency before minting
        if (rewardLedgerRepository.existsByUserAndRef(userId, reason, refType, refId)) {
            log.info("Reward already ledgered for user={} reason={} refType={} refId={}",
                    userId, reason, refType, refId);
            return false;
        }

        // Mint on-chain if user has wallet, otherwise credit off-chain
        String resolvedTxHash = txHash != null ? txHash : walletService.mintIdeaReward(userId, amount);

        rewardLedgerRepository.insertIfAbsent(userId, reason, refType, refId, resolvedTxHash, amount);
        log.info("Reward accrued: user={} reason={} refType={} refId={} amount={} txHash={}",
                userId, reason, refType, refId, amount, resolvedTxHash);
        return true;
    }
}
