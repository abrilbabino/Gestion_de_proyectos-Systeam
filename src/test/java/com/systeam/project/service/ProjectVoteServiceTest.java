package com.systeam.project.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

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

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeaGovernanceService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.project.repository.ProjectVoteRepository;
import com.systeam.rewards.service.RewardService;
import com.systeam.shared.model.Proyecto;
import com.systeam.voteeconomics.VoteEconomicsConfig;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
import com.systeam.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectVoteService")
class ProjectVoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final String TX_HASH = "0xabc123";
    private static final BigDecimal VOTE_COST = new BigDecimal("1.0");
    private static final BigDecimal VOTE_REWARD = new BigDecimal("0.5");
    private static final Long TREASURY_USER_ID = 99L;

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectVoteRepository voteRepository;
    @Mock private WalletService walletService;
    @Mock private VoteEconomicsConfigService configService;
    @Mock private RewardService rewardService;
    @Mock private IdeaGovernanceService onChainService;
    @Mock private BlockchainService blockchainService;
    @Mock private ProjectVoteStreamRegistry streamRegistry;

    private ProjectVoteService service;

    @BeforeEach
    void setUp() {
        service = new ProjectVoteService(projectRepository, voteRepository, walletService,
                configService, rewardService, onChainService, blockchainService, streamRegistry);
    }

    private Proyecto projectInState(String estado) {
        Proyecto p = new Proyecto();
        p.setId(PROJECT_ID);
        p.setEstado(estado);
        p.setTitulo("Test Project");
        return p;
    }

    @Nested
    @DisplayName("validateVote")
    class ValidateVote {

        @Test
        @DisplayName("valid vote on EJECUCION project passes")
        void validVote() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectInState("EJECUCION")));
            when(voteRepository.hasVoted(PROJECT_ID, USER_ID)).thenReturn(false);
            when(configService.loadConfig()).thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));

            service.validateVote(USER_ID, PROJECT_ID);

            verify(walletService).checkSufficientBalance(USER_ID, VOTE_COST);
        }

        @Test
        @DisplayName("project not found throws ResourceNotFoundException")
        void projectNotFound() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateVote(USER_ID, PROJECT_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("project not in EJECUCION throws ConflictException")
        void wrongState() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectInState("FINANCIAMIENTO")));

            assertThatThrownBy(() -> service.validateVote(USER_ID, PROJECT_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("EJECUCION");
        }

        @Test
        @DisplayName("already voted throws ConflictException")
        void alreadyVoted() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectInState("EJECUCION")));
            when(voteRepository.hasVoted(PROJECT_ID, USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.validateVote(USER_ID, PROJECT_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Ya votaste");
        }

        @Test
        @DisplayName("insufficient balance throws ConflictException")
        void insufficientBalance() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectInState("EJECUCION")));
            when(voteRepository.hasVoted(PROJECT_ID, USER_ID)).thenReturn(false);
            when(configService.loadConfig()).thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
            doThrow(new ConflictException("Saldo insuficiente para realizar la operación"))
                    .when(walletService).checkSufficientBalance(USER_ID, VOTE_COST);

            assertThatThrownBy(() -> service.validateVote(USER_ID, PROJECT_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Saldo insuficiente");
        }
    }

    @Nested
    @DisplayName("recordVote")
    class RecordVote {

        @BeforeEach
        void setUpConfig() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
            when(voteRepository.getVoteCounts(PROJECT_ID)).thenReturn(new long[]{1, 0, 1});
        }

        @Test
        @DisplayName("records vote: deducts cost, inserts vote, increments counts, accrues reward, broadcasts SSE")
        void fullVoteLifecycle() {
            when(rewardService.accrue(eq(USER_ID), eq("VOTE_REWARD"), eq("PROJECT"),
                    eq(PROJECT_ID), eq(TX_HASH), eq(VOTE_REWARD))).thenReturn(true);

            service.recordVote(USER_ID, PROJECT_ID, true, TX_HASH);

            verify(walletService).adjustBalance(USER_ID, VOTE_COST.negate());
            verify(walletService).adjustBalance(TREASURY_USER_ID, VOTE_COST);
            verify(voteRepository).insertVote(PROJECT_ID, USER_ID, true, TX_HASH);
            verify(voteRepository).incrementVoteCounts(PROJECT_ID, true);
            verify(rewardService).accrue(USER_ID, "VOTE_REWARD", "PROJECT", PROJECT_ID, TX_HASH, VOTE_REWARD);
            verify(streamRegistry).broadcast(eq(PROJECT_ID), eq(1L), eq(0L));
        }

        @Test
        @DisplayName("against vote increments against_votes")
        void againstVote() {
            when(voteRepository.getVoteCounts(PROJECT_ID)).thenReturn(new long[]{0, 1, 1});

            service.recordVote(USER_ID, PROJECT_ID, false, TX_HASH);

            verify(voteRepository).insertVote(PROJECT_ID, USER_ID, false, TX_HASH);
            verify(voteRepository).incrementVoteCounts(PROJECT_ID, false);
        }

        @Test
        @DisplayName("zero cost skips deduction")
        void zeroCost() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(BigDecimal.ZERO, VOTE_REWARD, TREASURY_USER_ID));

            service.recordVote(USER_ID, PROJECT_ID, true, TX_HASH);

            verify(walletService, never()).adjustBalance(anyLong(), any());
        }

        @Test
        @DisplayName("zero reward skips accrual")
        void zeroReward() {
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, BigDecimal.ZERO, TREASURY_USER_ID));

            service.recordVote(USER_ID, PROJECT_ID, true, TX_HASH);

            verify(rewardService, never()).accrue(anyLong(), any(), any(), anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("vote (full flow with on-chain)")
    class VoteFullFlow {

        @Test
        @DisplayName("uses existing on-chain proposal ID")
        void existingOnChainProposal() throws Exception {
            when(voteRepository.getOnChainProposalId(PROJECT_ID)).thenReturn(5L);
            when(onChainService.vote(BigInteger.valueOf(5L), true)).thenReturn(TX_HASH);
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
            when(voteRepository.getVoteCounts(PROJECT_ID)).thenReturn(new long[]{1, 0, 1});

            String result = service.vote(USER_ID, PROJECT_ID, true);

            assertThat(result).isEqualTo(TX_HASH);
            verify(onChainService, never()).createProposal(any(), any(), any());
        }

        @Test
        @DisplayName("creates on-chain proposal on first vote")
        void createsOnChainProposal() throws Exception {
            when(voteRepository.getOnChainProposalId(PROJECT_ID)).thenReturn(null);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectInState("EJECUCION")));
            when(onChainService.createProposal(any(), eq(0), any())).thenReturn("0xcreate");
            when(blockchainService.verifyTransaction("0xcreate")).thenReturn(true);
            when(onChainService.getProposalCount()).thenReturn(BigInteger.valueOf(7));
            when(onChainService.vote(BigInteger.valueOf(7L), true)).thenReturn(TX_HASH);
            when(configService.loadConfig())
                    .thenReturn(new VoteEconomicsConfig(VOTE_COST, VOTE_REWARD, TREASURY_USER_ID));
            when(voteRepository.getVoteCounts(PROJECT_ID)).thenReturn(new long[]{1, 0, 1});

            String result = service.vote(USER_ID, PROJECT_ID, true);

            assertThat(result).isEqualTo(TX_HASH);
            verify(voteRepository).setOnChainProposalId(PROJECT_ID, 7L);
        }
    }
}
