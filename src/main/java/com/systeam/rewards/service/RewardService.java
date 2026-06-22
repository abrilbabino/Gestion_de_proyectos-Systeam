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
    @Transactional
    public boolean accrue(Long userId, String reason, String refType,
                          Long refId, String txHash, BigDecimal amount) {
        boolean inserted = rewardLedgerRepository.insertIfAbsent(
                userId, reason, refType, refId, txHash, amount);

        if (!inserted) {
            log.info("Reward already ledgered for user={} reason={} refType={} refId={}",
                    userId, reason, refType, refId);
            return false;
        }

        walletService.adjustBalance(userId, amount);
        log.info("Reward accrued: user={} reason={} refType={} refId={} amount={}",
                userId, reason, refType, refId, amount);
        return true;
    }
}
