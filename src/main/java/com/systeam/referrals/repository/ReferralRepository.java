package com.systeam.referrals.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReferralRepository {

    private final JdbcTemplate jdbc;

    public ReferralRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getOrGenerateCode(Long userId) {
        String existing = jdbc.queryForList(
            "SELECT code FROM user_referral_codes WHERE user_id = ?", String.class, userId
        ).stream().findFirst().orElse(null);
        if (existing != null) return existing;

        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update(
            "INSERT INTO user_referral_codes (user_id, code) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING",
            userId, code
        );
        return jdbc.queryForObject("SELECT code FROM user_referral_codes WHERE user_id = ?", String.class, userId);
    }

    public Optional<Long> findReferrerByCode(String code) {
        return jdbc.queryForList(
            "SELECT user_id FROM user_referral_codes WHERE code = ?", Long.class, code
        ).stream().findFirst();
    }

    public Long insertReferral(Long referrerId, Long refereeId, String code) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO referrals (referrer_id, referee_id, code) VALUES (?, ?, ?)",
                new String[]{"id"}
            );
            ps.setLong(1, referrerId);
            ps.setLong(2, refereeId);
            ps.setString(3, code);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int countByReferrer(Long referrerId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM referrals WHERE referrer_id = ?", Integer.class, referrerId
        );
        return count != null ? count : 0;
    }

    public boolean refereeExists(Long refereeId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM referrals WHERE referee_id = ?", Integer.class, refereeId
        );
        return count != null && count > 0;
    }
}
