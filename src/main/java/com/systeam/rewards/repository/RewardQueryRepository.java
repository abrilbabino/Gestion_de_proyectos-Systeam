package com.systeam.rewards.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.systeam.rewards.dto.RewardHistoryItem;

@Repository
public class RewardQueryRepository {

    private final JdbcTemplate jdbc;

    public RewardQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns paginated reward history for a user, ordered by most recent first.
     */
    public List<RewardHistoryItem> findHistory(Long userId, int page, int size) {
        int offset = page * size;
        return jdbc.query(
            "SELECT id, reason, ref_type, ref_id, tx_hash, amount, created_at " +
            "FROM reward_ledger WHERE user_id = ? " +
            "ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> RewardHistoryItem.builder()
                .id(rs.getLong("id"))
                .reason(rs.getString("reason"))
                .refType(rs.getString("ref_type"))
                .refId(rs.getLong("ref_id"))
                .txHash(rs.getString("tx_hash"))
                .amount(rs.getBigDecimal("amount"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(),
            userId, size, offset
        );
    }

    /**
     * Returns total rewards and breakdown by reason for a user.
     */
    public Map<String, BigDecimal> findSummaryByUser(Long userId) {
        Map<String, BigDecimal> summary = new LinkedHashMap<>();

        // Total
        BigDecimal total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM reward_ledger WHERE user_id = ?",
            BigDecimal.class, userId
        );
        summary.put("total", total);

        // Breakdown by reason
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT reason, COALESCE(SUM(amount), 0) AS total_amount " +
            "FROM reward_ledger WHERE user_id = ? GROUP BY reason",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", rs.getString("reason"));
                row.put("total_amount", rs.getBigDecimal("total_amount"));
                return row;
            },
            userId
        );

        for (Map<String, Object> row : rows) {
            summary.put((String) row.get("reason"), (BigDecimal) row.get("total_amount"));
        }

        return summary;
    }
}
