package com.systeam.wallet.service;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.notificaciones.event.WalletTransferEvent;
import com.systeam.wallet.dto.TransferTokensRequest;
import com.systeam.wallet.dto.TransferTokensResponse;
import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WalletService service;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository, blockchainService, eventPublisher);
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        void retornaResumenCompleto() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("500.00"));
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(new BigDecimal("1000.00"));
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(List.<Object[]>of(
                new Object[]{1L, "ProyectoA", "TokenA", "TKA", 10, new BigDecimal("50.00"), "0xaddr1", "ACTIVO"},
                new Object[]{2L, "ProyectoB", "TokenB", "TKB", 5, new BigDecimal("30.00"), "0xaddr2", "ACTIVO"}
            ));

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getBalances().getIdea()).isEqualByComparingTo("500.00");
            assertThat(result.getBalances().getUsdt()).isEqualByComparingTo("1000.00");
            assertThat(result.getPortfolio()).hasSize(2);
            assertThat(result.getPortfolio().get(0).getSubtokenNombre()).isEqualTo("TokenA");
            assertThat(result.getPortfolio().get(0).getCantidad()).isEqualTo(10);
            assertThat(result.getPortfolio().get(1).getSubtokenNombre()).isEqualTo("TokenB");
            assertThat(result.getPortfolio().get(1).getCantidad()).isEqualTo(5);
        }

        @Test
        void cuandoSaldoIdeaEsNull_usaCero() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(null);
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(new BigDecimal("100.00"));
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(java.util.Collections.emptyList());

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getBalances().getIdea()).isEqualByComparingTo("0");
            assertThat(result.getBalances().getUsdt()).isEqualByComparingTo("100.00");
        }

        @Test
        void cuandoSaldoUsdtEsNull_usaCero() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("200.00"));
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(null);
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(java.util.Collections.emptyList());

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getBalances().getIdea()).isEqualByComparingTo("200.00");
            assertThat(result.getBalances().getUsdt()).isEqualByComparingTo("0");
        }

        @Test
        void cuandoAmbosSaldosNull_usaCero() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(null);
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(null);
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(java.util.Collections.emptyList());

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getBalances().getIdea()).isEqualByComparingTo("0");
            assertThat(result.getBalances().getUsdt()).isEqualByComparingTo("0");
            assertThat(result.getPortfolio()).isEmpty();
        }

        @Test
        void portfolioVacio_retornaListaVacia() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(BigDecimal.TEN);
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(BigDecimal.ONE);
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(java.util.Collections.emptyList());

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getPortfolio()).isEmpty();
        }

        @Test
        void unSoloItemEnPortfolio() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(BigDecimal.ZERO);
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(BigDecimal.ZERO);
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(List.<Object[]>of(
                new Object[]{1L, "ProyectoUnico", "UnicoToken", "UNI", 1, new BigDecimal("99.99"), "0xaddr", "ACTIVO"}
            ));

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getPortfolio()).hasSize(1);
            assertThat(result.getPortfolio().get(0).getSubtokenNombre()).isEqualTo("UnicoToken");
            assertThat(result.getPortfolio().get(0).getCantidad()).isEqualTo(1);
            assertThat(result.getPortfolio().get(0).getPrecioActual()).isEqualByComparingTo("99.99");
        }
    }

    @Nested
    @DisplayName("transferTokens")
    class TransferTokens {

        private static final Long DESTINATARIO_ID = 2L;
        private static final String EMAIL_EMISOR = "emisor@test.com";
        private static final String EMAIL_DEST = "dest@test.com";
        private static final String TX_HASH = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        private static final String WALLET_EMISOR = "0x1234567890123456789012345678901234567890";

        @Test
        @DisplayName("transferencia exitosa — publica WalletTransferEvent, no llama EmailService")
        void transferExitosa_publicaEvento() throws Exception {
            TransferTokensRequest request = new TransferTokensRequest();
            request.setDestinatarioId(DESTINATARIO_ID);
            request.setCantidad(new BigDecimal("50.00"));
            request.setTxHash(TX_HASH);
            request.setWalletEmisor(WALLET_EMISOR);

            when(walletRepository.userExists(DESTINATARIO_ID)).thenReturn(true);
            when(walletRepository.txHashExists(TX_HASH)).thenReturn(false);
            doReturn(true).when(blockchainService).verifyTransaction(TX_HASH);
            doReturn(WALLET_EMISOR).when(blockchainService).getSenderFromTx(TX_HASH);
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("100.00"));
            when(walletRepository.saveTransfer(USER_ID, DESTINATARIO_ID, new BigDecimal("50.00"), TX_HASH))
                .thenReturn(TransferTokensResponse.builder()
                    .id(1L).emisorId(USER_ID).destinatarioId(DESTINATARIO_ID)
                    .cantidad(new BigDecimal("50.00")).txHash(TX_HASH).build());

            TransferTokensResponse result = service.transferTokens(USER_ID, EMAIL_EMISOR, request);

            assertThat(result.getEmisorId()).isEqualTo(USER_ID);
            assertThat(result.getDestinatarioId()).isEqualTo(DESTINATARIO_ID);
            verify(eventPublisher).publishEvent(any(WalletTransferEvent.class));
        }
    }
}
