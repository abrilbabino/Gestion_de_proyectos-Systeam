package com.systeam.wallet.service;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private WalletRepository walletRepository;

    private WalletService service;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository);
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        void retornaResumenCompleto() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("500.00"));
            when(walletRepository.findSaldoUsdt(USER_ID)).thenReturn(new BigDecimal("1000.00"));
            when(walletRepository.findPortfolio(USER_ID)).thenReturn(List.<Object[]>of(
                new Object[]{1L, "ProyectoA", "TokenA", "TKA", 10, new BigDecimal("50.00"), "0xaddr1"},
                new Object[]{2L, "ProyectoB", "TokenB", "TKB", 5, new BigDecimal("30.00"), "0xaddr2"}
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
                new Object[]{1L, "ProyectoUnico", "UnicoToken", "UNI", 1, new BigDecimal("99.99"), "0xaddr"}
            ));

            WalletSummaryResponse result = service.getSummary(USER_ID);

            assertThat(result.getPortfolio()).hasSize(1);
            assertThat(result.getPortfolio().get(0).getSubtokenNombre()).isEqualTo("UnicoToken");
            assertThat(result.getPortfolio().get(0).getCantidad()).isEqualTo(1);
            assertThat(result.getPortfolio().get(0).getPrecioActual()).isEqualByComparingTo("99.99");
        }
    }
}
