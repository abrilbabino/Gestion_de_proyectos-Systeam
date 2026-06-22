package com.systeam.rewards.repository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RewardLedgerRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    private RewardLedgerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RewardLedgerRepository(jdbc);
    }

    @Nested
    @DisplayName("insertIfAbsent")
    class InsertIfAbsent {

        @Test
        @DisplayName("returns true when row is inserted (no prior entry)")
        void returnsTrueOnInsert() {
            when(jdbc.update(anyString(),
                    eq(1L), eq("VOTE_REWARD"), eq("PROPOSAL"), eq(42L),
                    eq("0xabc"), eq(new BigDecimal("0.5"))))
                    .thenReturn(1);

            boolean result = repository.insertIfAbsent(
                    1L, "VOTE_REWARD", "PROPOSAL", 42L, "0xabc", new BigDecimal("0.5"));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when duplicate exists (ON CONFLICT DO NOTHING)")
        void returnsFalseOnDuplicate() {
            when(jdbc.update(anyString(),
                    eq(2L), eq("VOTE_REWARD"), eq("PROPOSAL"), eq(42L),
                    eq("0xdef"), eq(new BigDecimal("0.5"))))
                    .thenReturn(0);

            boolean result = repository.insertIfAbsent(
                    2L, "VOTE_REWARD", "PROPOSAL", 42L, "0xdef", new BigDecimal("0.5"));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("different reason allows insert for same user/ref")
        void differentReasonAllowsInsert() {
            when(jdbc.update(anyString(),
                    eq(1L), eq("VOTE_COST"), eq("PROPOSAL"), eq(42L),
                    eq("0xabc"), eq(new BigDecimal("1.0"))))
                    .thenReturn(1);

            boolean result = repository.insertIfAbsent(
                    1L, "VOTE_COST", "PROPOSAL", 42L, "0xabc", new BigDecimal("1.0"));

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("existsByUserAndRef")
    class ExistsByUserAndRef {

        @Test
        @DisplayName("returns true when ledger entry exists")
        void returnsTrueWhenExists() {
            when(jdbc.queryForObject(anyString(), eq(Integer.class),
                    eq("VOTE_REWARD"), eq("PROPOSAL"), eq(42L), eq(1L)))
                    .thenReturn(1);

            boolean result = repository.existsByUserAndRef(1L, "VOTE_REWARD", "PROPOSAL", 42L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when no ledger entry exists")
        void returnsFalseWhenNotExists() {
            when(jdbc.queryForObject(anyString(), eq(Integer.class),
                    eq("VOTE_REWARD"), eq("PROPOSAL"), eq(99L), eq(5L)))
                    .thenReturn(0);

            boolean result = repository.existsByUserAndRef(5L, "VOTE_REWARD", "PROPOSAL", 99L);

            assertThat(result).isFalse();
        }
    }
}
