package com.systeam.wallet.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.project.exception.ConflictException;
import com.systeam.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceAdjustBalanceTest {

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
    @DisplayName("checkSufficientBalance")
    class CheckSufficientBalance {

        @Test
        @DisplayName("passes when balance is sufficient")
        void passesWhenSufficient() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("100.0"));

            service.checkSufficientBalance(USER_ID, new BigDecimal("50.0"));

            verify(walletRepository, never()).adjustSaldoIdea(any(), any());
        }

        @Test
        @DisplayName("throws ConflictException when balance is insufficient")
        void throwsWhenInsufficient() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("5.0"));

            assertThatThrownBy(() -> service.checkSufficientBalance(USER_ID, new BigDecimal("10.0")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Saldo insuficiente");
        }

        @Test
        @DisplayName("throws ConflictException when balance is null")
        void throwsWhenNull() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.checkSufficientBalance(USER_ID, new BigDecimal("1.0")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("passes when balance is exactly equal to required")
        void passesWhenExact() {
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("10.0"));

            service.checkSufficientBalance(USER_ID, new BigDecimal("10.0"));
        }
    }

    @Nested
    @DisplayName("adjustBalance")
    class AdjustBalance {

        @Test
        @DisplayName("positive amount credits user balance without checking balance")
        void positiveAmountCreditsBalance() {
            BigDecimal amount = new BigDecimal("5.0");

            service.adjustBalance(USER_ID, amount);

            verify(walletRepository).adjustSaldoIdea(USER_ID, amount);
            verify(walletRepository, never()).findSaldoIdea(any());
        }

        @Test
        @DisplayName("negative amount debits user balance when sufficient")
        void negativeAmountDebitsWhenSufficient() {
            BigDecimal amount = new BigDecimal("-10.0");
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("100.0"));

            service.adjustBalance(USER_ID, amount);

            verify(walletRepository).adjustSaldoIdea(USER_ID, amount);
        }

        @Test
        @DisplayName("negative amount throws ConflictException when insufficient balance")
        void negativeAmountThrowsWhenInsufficient() {
            BigDecimal amount = new BigDecimal("-50.0");
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("10.0"));

            assertThatThrownBy(() -> service.adjustBalance(USER_ID, amount))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Saldo insuficiente");

            verify(walletRepository, never()).adjustSaldoIdea(any(), any());
        }

        @Test
        @DisplayName("exact balance allows debit (edge case)")
        void exactBalanceAllowsDebit() {
            BigDecimal amount = new BigDecimal("-100.0");
            when(walletRepository.findSaldoIdea(USER_ID)).thenReturn(new BigDecimal("100.0"));

            service.adjustBalance(USER_ID, amount);

            verify(walletRepository).adjustSaldoIdea(USER_ID, amount);
        }
    }
}
