package com.systeam.rewards.repository;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RewardLedgerRepository {

    private final JdbcTemplate jdbc;

    public RewardLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a reward ledger entry idempotently.
     * Returns true if the row was inserted, false if it already existed
     * (ON CONFLICT DO NOTHING).
     */
    public boolean insertIfAbsent(Long userId, String reason, String refType,
                                   Long refId, String txHash, BigDecimal amount) {
        int rows = jdbc.update(
                "INSERT INTO reward_ledger (user_id, reason, ref_type, ref_id, tx_hash, amount) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (reason, ref_type, ref_id, user_id) DO NOTHING",
                userId, reason, refType, refId, txHash, amount);
        return rows > 0;
    }

    /**
     * Checks whether a ledger entry exists for the given user and reference.
     */
    public boolean existsByUserAndRef(Long userId, String reason, String refType, Long refId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reward_ledger WHERE reason = ? AND ref_type = ? AND ref_id = ? AND user_id = ?",
                Integer.class, reason, refType, refId, userId);
        return count != null && count > 0;
    }
}
