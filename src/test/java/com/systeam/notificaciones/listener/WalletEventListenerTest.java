package com.systeam.notificaciones.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.systeam.notificaciones.event.WalletTransferEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;
import com.systeam.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private WalletRepository walletRepository;

    private WalletEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WalletEventListener(notificationService, emailService, walletRepository);
    }

    @Test
    void onWalletTransfer_createsNotificationAndSendsEmailToBoth() {
        var event = new WalletTransferEvent(1L, 2L, new BigDecimal("500.00"), "0xdef456");
        when(walletRepository.findEmailById(1L)).thenReturn("emisor@test.com");
        when(walletRepository.findEmailById(2L)).thenReturn("destinatario@test.com");
        when(walletRepository.findNameById(1L)).thenReturn("Juan");
        when(walletRepository.findNameById(2L)).thenReturn("María");

        listener.onWalletTransfer(event);

        verify(notificationService).createNotification(
            eq(1L), eq("WALLET_TRANSFER_SENT"), eq("Transferencia enviada"),
            eq("Has enviado 500.00 IDEA a María."),
            eq("{\"cantidad\":500.00,\"destinatarioId\":2,\"txHash\":\"0xdef456\"}")
        );
        verify(notificationService).createNotification(
            eq(2L), eq("WALLET_TRANSFER_RECEIVED"), eq("Transferencia recibida"),
            eq("Has recibido 500.00 IDEA de Juan."),
            eq("{\"cantidad\":500.00,\"emisorId\":1,\"txHash\":\"0xdef456\"}")
        );
        verify(emailService).sendEmail(
            eq("emisor@test.com"), eq("Transferencia enviada"),
            eq("Has enviado 500.00 IDEA a María. Tx: 0xdef456")
        );
        verify(emailService).sendEmail(
            eq("destinatario@test.com"), eq("Transferencia recibida"),
            eq("Has recibido 500.00 IDEA de Juan. Tx: 0xdef456")
        );
    }

    @Test
    void onWalletTransfer_whenException_swallows() {
        var event = new WalletTransferEvent(1L, 2L, new BigDecimal("500.00"), "0xdef456");
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(1L), eq("WALLET_TRANSFER_SENT"), anyString(), anyString(), anyString()
            );

        listener.onWalletTransfer(event);
    }
}
