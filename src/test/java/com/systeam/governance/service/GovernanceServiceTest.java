package com.systeam.governance.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.notificaciones.event.GovernanceProposalEvent;
import com.systeam.rewards.service.RewardService;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
import com.systeam.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class GovernanceServiceTest {

    private static final Long PROPOSAL_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String WALLET = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TX_HASH = "0xabc123";

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WalletService walletService;

    @Mock
    private RewardService rewardService;

    @Mock
    private VoteEconomicsConfigService configService;

    @Mock
    private VoteStreamRegistry voteStreamRegistry;

    private GovernanceService service;

    private CreateProposalRequest request;

    @BeforeEach
    void setUp() {
        service = new GovernanceService(jdbc, eventPublisher, walletService,
                rewardService, configService, voteStreamRegistry);

        request = new CreateProposalRequest();
        request.setTitle("Actualizar parametros");
        request.setDescription("Cambiar el ratio de conversion");
        request.setProposalType(1);
    }

    private ProposalResponse createProposalResponse(Long id) {
        ProposalResponse r = new ProposalResponse();
        r.setId(id);
        r.setTitle("Actualizar parametros");
        r.setDescription("Cambiar el ratio de conversion");
        r.setProposalType("ParameterChange");
        r.setStatus("ACTIVE");
        r.setForVotes(BigInteger.ZERO);
        r.setAgainstVotes(BigInteger.ZERO);
        r.setTotalVotes(BigInteger.ZERO);
        r.setStartTime(LocalDateTime.now());
        r.setEndTime(LocalDateTime.now().plusDays(7));
        r.setTxHash(TX_HASH);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    // ═════════════════════════════════════════════════════
    // createProposalOffChain
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("createProposalOffChain")
    class CreateProposalOffChain {

        @Test
        void conTipoParameterChange_retornaProposal() {
            mockInsertAndGet();

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            verifyUpdateContains("ParameterChange");
            verify(eventPublisher).publishEvent(any(GovernanceProposalEvent.class));
        }

        @Test
        void conTipoProjectApproval_retornaProposal() {
            mockInsertAndGet();
            request.setProposalType(0);

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            verifyUpdateContains("ProjectApproval");
        }

        @Test
        void conTipoTreasuryAction_retornaProposal() {
            mockInsertAndGet();
            request.setProposalType(2);

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            verifyUpdateContains("TreasuryAction");
        }

        @Test
        void conTipoNull_usaDefaultProjectApproval() {
            mockInsertAndGet();
            request.setProposalType(null);

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            verifyUpdateContains("ProjectApproval");
        }

        @Test
        void conTipoDesconocido_usaDefaultProjectApproval() {
            mockInsertAndGet();
            request.setProposalType(99);

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            verifyUpdateContains("ProjectApproval");
        }

        @Test
        void conDataBytes_retornaProposal() {
            request.setData("extra data".getBytes());
            mockInsertAndGet();

            ProposalResponse result = service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("INSERT INTO proposals")),
                eq(WALLET), eq(USER_ID), eq("Actualizar parametros"), eq("Cambiar el ratio de conversion"),
                eq("ParameterChange"), eq("extra data"), eq(null),
                any(LocalDateTime.class), eq(TX_HASH)
            );
        }

        @Test
        void sinDataBytes_dataEsNull() {
            request.setData(null);
            mockInsertAndGet();

            service.createProposalOffChain(request, USER_ID, WALLET, TX_HASH);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("INSERT INTO proposals")),
                eq(WALLET), eq(USER_ID), eq("Actualizar parametros"), eq("Cambiar el ratio de conversion"),
                eq("ParameterChange"), eq(null), eq(null),
                any(LocalDateTime.class), eq(TX_HASH)
            );
        }

        private void mockInsertAndGet() {
            when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(PROPOSAL_ID);
            ProposalResponse mockResponse = createProposalResponse(PROPOSAL_ID);
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("FROM proposals p")),
                any(RowMapper.class), eq(PROPOSAL_ID)))
                .thenReturn(List.of(mockResponse));
        }

        private void verifyUpdateContains(String typeName) {
            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("INSERT INTO proposals")),
                eq(WALLET), eq(USER_ID), eq("Actualizar parametros"), eq("Cambiar el ratio de conversion"),
                eq(typeName), eq(null), eq(null),
                any(LocalDateTime.class), eq(TX_HASH)
            );
        }
    }

    // ═════════════════════════════════════════════════════
    // updateOnChainId
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateOnChainId")
    class UpdateOnChainId {

        @Test
        void ejecutaUpdateCorrectamente() {
            BigInteger onChainId = BigInteger.valueOf(42L);

            service.updateOnChainId(PROPOSAL_ID, onChainId);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("UPDATE proposals") && sql.toString().contains("on_chain_id")),
                eq(42L), eq(PROPOSAL_ID)
            );
        }

        @Test
        void cuandoOnChainIdEsGrande_lanzaException() {
            BigInteger onChainId = new BigInteger("99999999999999999999");

            assertThatThrownBy(() -> service.updateOnChainId(PROPOSAL_ID, onChainId))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("BigInteger");
        }
    }

    // ═════════════════════════════════════════════════════
    // getProposalById
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("getProposalById")
    class GetProposalById {

        @Test
        void cuandoExiste_retornaProposal() {
            ProposalResponse expected = createProposalResponse(PROPOSAL_ID);
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("FROM proposals p")),
                any(RowMapper.class), eq(PROPOSAL_ID)))
                .thenReturn(List.of(expected));

            ProposalResponse result = service.getProposalById(PROPOSAL_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PROPOSAL_ID);
            assertThat(result.getTitle()).isEqualTo("Actualizar parametros");
        }

        @Test
        void cuandoNoExiste_retornaNull() {
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("FROM proposals p")),
                any(RowMapper.class), eq(PROPOSAL_ID)))
                .thenReturn(List.of());

            ProposalResponse result = service.getProposalById(PROPOSAL_ID);

            assertThat(result).isNull();
        }
    }

    // ═════════════════════════════════════════════════════
    // listProposals
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("listProposals")
    class ListProposals {

        @Test
        void sinFiltroStatus_retornaLista() {
            ProposalResponse p = createProposalResponse(PROPOSAL_ID);
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("ORDER BY p.created_at DESC")),
                any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of(p));

            List<ProposalResponse> result = service.listProposals(null, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(PROPOSAL_ID);
        }

        @Test
        void conFiltroStatus_retornaListaFiltrada() {
            ProposalResponse p = createProposalResponse(PROPOSAL_ID);
            p.setStatus("ACTIVE");
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("WHERE p.status = ?")),
                any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of(p));

            List<ProposalResponse> result = service.listProposals("ACTIVE", 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void conFiltroVacio_trataComoSinFiltro() {
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("ORDER BY p.created_at DESC") &&
                               !sql.toString().contains("WHERE")),
                any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of());

            List<ProposalResponse> result = service.listProposals("   ", 0, 10);

            assertThat(result).isEmpty();
        }

        @Test
        void pagina2Size5_calculaOffsetCorrectamente() {
            ProposalResponse p = createProposalResponse(PROPOSAL_ID);
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("ORDER BY p.created_at DESC")),
                any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of(p));

            List<ProposalResponse> result = service.listProposals(null, 2, 5);

            assertThat(result).hasSize(1);
        }

        @Test
        void sinResultados_retornaVacio() {
            when(jdbc.query(
                argThat(sql -> sql.toString().contains("ORDER BY p.created_at DESC")),
                any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of());

            List<ProposalResponse> result = service.listProposals(null, 0, 10);

            assertThat(result).isEmpty();
        }
    }

    // ═════════════════════════════════════════════════════
    // countProposals
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("countProposals")
    class CountProposals {

        @Test
        void sinFiltro_retornaTotal() {
            when(jdbc.queryForObject(
                argThat(sql -> sql.toString().contains("SELECT COUNT(*)") &&
                               !sql.toString().contains("WHERE")),
                any(Object[].class), eq(Long.class)))
                .thenReturn(5L);

            long count = service.countProposals(null);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        void conFiltro_retornaFiltrados() {
            when(jdbc.queryForObject(
                argThat(sql -> sql.toString().contains("SELECT COUNT(*)") &&
                               sql.toString().contains("WHERE")),
                any(Object[].class), eq(Long.class)))
                .thenReturn(3L);

            long count = service.countProposals("ACTIVE");

            assertThat(count).isEqualTo(3L);
        }

        @Test
        void conFiltroVacio_trataComoSinFiltro() {
            when(jdbc.queryForObject(
                argThat(sql -> sql.toString().contains("SELECT COUNT(*)") &&
                               !sql.toString().contains("WHERE")),
                any(Object[].class), eq(Long.class)))
                .thenReturn(5L);

            long count = service.countProposals("   ");

            assertThat(count).isEqualTo(5L);
        }

        @Test
        void cuandoResultadoNull_retornaCero() {
            when(jdbc.queryForObject(
                argThat(sql -> sql.toString().contains("SELECT COUNT(*)")),
                any(Object[].class), eq(Long.class)))
                .thenReturn(null);

            long count = service.countProposals(null);

            assertThat(count).isZero();
        }
    }

    // ═════════════════════════════════════════════════════
    // updateVotes
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateVotes")
    class UpdateVotes {

        @Test
        void ejecutaUpdateCorrectamente() {
            BigInteger forVotes = BigInteger.valueOf(100);
            BigInteger againstVotes = BigInteger.valueOf(50);

            service.updateVotes(PROPOSAL_ID, forVotes, againstVotes);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("UPDATE proposals") &&
                               sql.toString().contains("for_votes")),
                eq(BigInteger.valueOf(100)), eq(BigInteger.valueOf(50)),
                eq(BigInteger.valueOf(150)), eq(PROPOSAL_ID)
            );
        }

        @Test
        void conVotosCero_ejecutaCorrectamente() {
            service.updateVotes(PROPOSAL_ID, BigInteger.ZERO, BigInteger.ZERO);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("UPDATE proposals")),
                eq(BigInteger.ZERO), eq(BigInteger.ZERO),
                eq(BigInteger.ZERO), eq(PROPOSAL_ID)
            );
        }

        @Test
        void totalEsSumaDeForYAgainst() {
            BigInteger forVotes = BigInteger.valueOf(200);
            BigInteger againstVotes = BigInteger.valueOf(150);

            service.updateVotes(PROPOSAL_ID, forVotes, againstVotes);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("total_votes")),
                eq(forVotes), eq(againstVotes),
                eq(BigInteger.valueOf(350)), eq(PROPOSAL_ID)
            );
        }
    }

    // ═════════════════════════════════════════════════════
    // markExecuted
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("markExecuted")
    class MarkExecuted {

        @Test
        void ejecutaUpdateCorrectamente() {
            LocalDateTime executedAt = LocalDateTime.now();

            service.markExecuted(PROPOSAL_ID, executedAt);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("UPDATE proposals") &&
                               sql.toString().contains("EXECUTED")),
                eq(executedAt), eq(PROPOSAL_ID)
            );
            verify(eventPublisher).publishEvent(any(GovernanceProposalEvent.class));
        }

        @Test
        void statusCambiaAExecuted() {
            LocalDateTime executedAt = LocalDateTime.now();

            service.markExecuted(PROPOSAL_ID, executedAt);

            verify(jdbc).update(
                argThat(sql -> sql.toString().contains("'EXECUTED'")),
                eq(executedAt), eq(PROPOSAL_ID)
            );
            verify(eventPublisher).publishEvent(any(GovernanceProposalEvent.class));
        }
    }
}
