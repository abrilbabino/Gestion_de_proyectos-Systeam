package com.systeam.voteeconomics;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VoteEconomicsConfigService {

    private static final VoteEconomicsConfig DEFAULTS = new VoteEconomicsConfig(
            BigDecimal.ZERO, BigDecimal.ZERO, null);

    private final JdbcTemplate jdbc;

    public VoteEconomicsConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public VoteEconomicsConfig loadConfig() {
        List<VoteEconomicsConfig> rows = jdbc.query(
                "SELECT vote_cost, vote_reward, treasury_user_id FROM vote_economics_config WHERE id = 1",
                (rs, rowNum) -> new VoteEconomicsConfig(
                        rs.getBigDecimal("vote_cost"),
                        rs.getBigDecimal("vote_reward"),
                        rs.getObject("treasury_user_id") != null ? rs.getLong("treasury_user_id") : null
                ));
        return rows.isEmpty() ? DEFAULTS : rows.get(0);
    }
}
