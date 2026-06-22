package com.systeam.voteeconomics;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class VoteEconomicsConfigServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private VoteEconomicsConfigService service;

    @BeforeEach
    void setUp() {
        service = new VoteEconomicsConfigService(jdbc);
    }

    @Nested
    @DisplayName("loadConfig")
    class LoadConfig {

        @Test
        @DisplayName("reads cost, reward, and treasury user from DB")
        void readsConfigFromDb() {
            VoteEconomicsConfig config = new VoteEconomicsConfig(
                    new BigDecimal("1.0"), new BigDecimal("0.5"), 99L);
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class)))
                    .thenReturn(List.of(config));

            VoteEconomicsConfig result = service.loadConfig();

            assertThat(result.getVoteCost()).isEqualByComparingTo("1.0");
            assertThat(result.getVoteReward()).isEqualByComparingTo("0.5");
            assertThat(result.getTreasuryUserId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("returns defaults when no config row exists")
        void returnsDefaultsWhenEmpty() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class)))
                    .thenReturn(Collections.emptyList());

            VoteEconomicsConfig result = service.loadConfig();

            assertThat(result.getVoteCost()).isEqualByComparingTo("0");
            assertThat(result.getVoteReward()).isEqualByComparingTo("0");
            assertThat(result.getTreasuryUserId()).isNull();
        }

        @Test
        @DisplayName("different config values produce different results")
        void differentConfigValues() {
            VoteEconomicsConfig config = new VoteEconomicsConfig(
                    new BigDecimal("10.0"), new BigDecimal("5.0"), 1L);
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class)))
                    .thenReturn(List.of(config));

            VoteEconomicsConfig result = service.loadConfig();

            assertThat(result.getVoteCost()).isEqualByComparingTo("10.0");
            assertThat(result.getVoteReward()).isEqualByComparingTo("5.0");
            assertThat(result.getTreasuryUserId()).isEqualTo(1L);
        }
    }
}
