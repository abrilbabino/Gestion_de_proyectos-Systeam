package com.systeam.governance.service;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.VoteRewardedEvent;
import com.systeam.project.exception.ConflictException;
import com.systeam.rewards.service.RewardService;
import com.systeam.voteeconomics.VoteEconomicsConfig;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
import com.systeam.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernanceService — vote economics")
class GovernanceServiceVoteEconomicsTest {

    private static final Long USER_ID = 1L;
    private static final Long PROPOSAL_ID = 42L;
    private static final String TX_HASH = "0xabc123def456";
    private static final BigDecimal VOTE_COST = new BigDecimal("10");
    private static final BigDecimal VOTE_REWARD = new BigDecimal("5");
    private static final Long TREASURY_USER_ID = 99L;

    @Mock private JdbcTemplate jdbc;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WalletService walletService;
    @Mock private RewardService rewardService;
    @Mock private VoteEconomicsConfigService configService;
    @Mock private VoteStreamRegistry voteStreamRegistry;

    private GovernanceService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceService(jdbc, eventPublisher, walletService,
                rewardService, configService, voteStreamRegistry);
    }

    @Nested
    @DisplayName("validateVoteCost")
    class ValidateVoteCost {

        @Test
        @DisplayName("sufficient balance passes without exception")
        void sufficientBalance() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));

            service.validateVoteCost(USER_ID);

            verify(walletService).checkSufficientBalance(USER_ID, VOTE_COST);
        }

        @Test
        @DisplayName("insufficient balance throws ConflictException")
        void insufficientBalance() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
            doThrow(new ConflictException("Saldo insuficiente para realizar la operación"))
                    .when(walletService).checkSufficientBalance(USER_ID, VOTE_COST);

            assertThatThrownBy(() -> service.validateVoteCost(USER_ID))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("zero cost config skips balance check")
        void zeroCostSkipsCheck() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(BigDecimal.ZERO, VOTE_REWARD, TREASURY_USER_ID));

            service.validateVoteCost(USER_ID);

            verify(walletService, never()).checkSufficientBalance(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("recordVoteEconomics")
    class RecordVoteEconomics {

        @BeforeEach
        void setUpConfig() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
        }

        @Test
        @DisplayName("successful vote: debits cost, accrues reward, publishes event, broadcasts SSE")
        void fullVoteLifecycle() {
            when(rewardService.accrue(eq(USER_ID), eq("VOTE_REWARD"), eq("PROPOSAL"),
                    eq(PROPOSAL_ID), eq(TX_HASH), eq(VOTE_REWARD)))
                    .thenReturn(true);

            service.recordVoteEconomics(USER_ID, PROPOSAL_ID, TX_HASH);

            // Cost deducted from voter
            verify(walletService).adjustBalance(USER_ID, VOTE_COST.negate());
            // Cost credited to treasury
            verify(walletService).adjustBalance(TREASURY_USER_ID, VOTE_COST);
            // Reward accrued
            verify(rewardService).accrue(USER_ID, "VOTE_REWARD", "PROPOSAL",
                    PROPOSAL_ID, TX_HASH, VOTE_REWARD);
            // Event published
            verify(eventPublisher).publishEvent(any(VoteRewardedEvent.class));
            // SSE broadcast
            verify(voteStreamRegistry).broadcast(eq(PROPOSAL_ID), any(), any());
        }

        @Test
        @DisplayName("insufficient balance throws ConflictException, no reward accrued")
        void insufficientBalance() {
            doThrow(new ConflictException("Saldo insuficiente para realizar la operación"))
                    .when(walletService).adjustBalance(eq(USER_ID), eq(VOTE_COST.negate()));

            assertThatThrownBy(() -> service.recordVoteEconomics(USER_ID, PROPOSAL_ID, TX_HASH))
                    .isInstanceOf(ConflictException.class);

            verify(rewardService, never()).accrue(anyLong(), any(), any(), anyLong(), any(), any());
            verify(eventPublisher, never()).publishEvent(any(VoteRewardedEvent.class));
        }

        @Test
        @DisplayName("duplicate reward returns false but vote still completes")
        void duplicateRewardNoDoubleCredit() {
            when(rewardService.accrue(eq(USER_ID), eq("VOTE_REWARD"), eq("PROPOSAL"),
                    eq(PROPOSAL_ID), eq(TX_HASH), eq(VOTE_REWARD)))
                    .thenReturn(false);

            service.recordVoteEconomics(USER_ID, PROPOSAL_ID, TX_HASH);

            // Cost still deducted
            verify(walletService).adjustBalance(USER_ID, VOTE_COST.negate());
            // Event still published (vote happened, reward was just idempotent)
            verify(eventPublisher).publishEvent(any(VoteRewardedEvent.class));
        }

        @Test
        @DisplayName("zero cost config skips deduction but still accrues reward")
        void zeroCostSkipsDeduction() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(BigDecimal.ZERO, VOTE_REWARD, TREASURY_USER_ID));
            when(rewardService.accrue(eq(USER_ID), eq("VOTE_REWARD"), eq("PROPOSAL"),
                    eq(PROPOSAL_ID), eq(TX_HASH), eq(VOTE_REWARD)))
                    .thenReturn(true);

            service.recordVoteEconomics(USER_ID, PROPOSAL_ID, TX_HASH);

            verify(walletService, never()).adjustBalance(eq(USER_ID), any());
            verify(walletService, never()).adjustBalance(eq(TREASURY_USER_ID), any());
            verify(rewardService).accrue(USER_ID, "VOTE_REWARD", "PROPOSAL",
                    PROPOSAL_ID, TX_HASH, VOTE_REWARD);
        }

        @Test
        @DisplayName("zero reward config skips accrual but still deducts cost")
        void zeroRewardSkipsAccrual() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, BigDecimal.ZERO, TREASURY_USER_ID));

            service.recordVoteEconomics(USER_ID, PROPOSAL_ID, TX_HASH);

            verify(walletService).adjustBalance(USER_ID, VOTE_COST.negate());
            verify(walletService).adjustBalance(TREASURY_USER_ID, VOTE_COST);
            verify(rewardService, never()).accrue(anyLong(), any(), any(), anyLong(), any(), any());
        }
    }
}
