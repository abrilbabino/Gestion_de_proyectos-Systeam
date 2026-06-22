package com.systeam.rewards.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.systeam.rewards.repository.RewardLedgerRepository;
import com.systeam.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long REF_ID = 42L;
    private static final String TX_HASH = "0xabc123";
    private static final BigDecimal REWARD_AMOUNT = new BigDecimal("0.5");

    @Mock
    private RewardLedgerRepository rewardLedgerRepository;

    @Mock
    private WalletService walletService;

    private RewardService service;

    @BeforeEach
    void setUp() {
        service = new RewardService(rewardLedgerRepository, walletService);
    }

    @Nested
    @DisplayName("accrue")
    class Accrue {

        @Test
        @DisplayName("first accrue inserts ledger row, credits wallet, returns true")
        void firstAccrueSucceeds() {
            when(rewardLedgerRepository.insertIfAbsent(
                    eq(USER_ID), eq("VOTE_REWARD"), eq("PROPOSAL"), eq(REF_ID),
                    eq(TX_HASH), eq(REWARD_AMOUNT)))
                    .thenReturn(true);

            boolean result = service.accrue(USER_ID, "VOTE_REWARD", "PROPOSAL",
                    REF_ID, TX_HASH, REWARD_AMOUNT);

            assertThat(result).isTrue();
            verify(walletService).adjustBalance(USER_ID, REWARD_AMOUNT);
            verify(rewardLedgerRepository).insertIfAbsent(
                    USER_ID, "VOTE_REWARD", "PROPOSAL", REF_ID, TX_HASH, REWARD_AMOUNT);
        }

        @Test
        @DisplayName("duplicate accrue returns false, does NOT credit wallet")
        void duplicateAccrueReturnsFalse() {
            when(rewardLedgerRepository.insertIfAbsent(
                    eq(USER_ID), eq("VOTE_REWARD"), eq("PROPOSAL"), eq(REF_ID),
                    eq(TX_HASH), eq(REWARD_AMOUNT)))
                    .thenReturn(false);

            boolean result = service.accrue(USER_ID, "VOTE_REWARD", "PROPOSAL",
                    REF_ID, TX_HASH, REWARD_AMOUNT);

            assertThat(result).isFalse();
            verify(walletService, never()).adjustBalance(any(), any());
        }

        @Test
        @DisplayName("event attendance reward with different reason works")
        void eventAttendanceReward() {
            BigDecimal eventReward = new BigDecimal("20.0");
            when(rewardLedgerRepository.insertIfAbsent(
                    eq(3L), eq("EVENT_ATTENDANCE"), eq("EVENT"), eq(7L),
                    eq(null), eq(eventReward)))
                    .thenReturn(true);

            boolean result = service.accrue(3L, "EVENT_ATTENDANCE", "EVENT",
                    7L, null, eventReward);

            assertThat(result).isTrue();
            verify(walletService).adjustBalance(3L, eventReward);
        }
    }
}
