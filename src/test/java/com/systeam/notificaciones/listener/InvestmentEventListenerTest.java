package com.systeam.notificaciones.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.InvestmentConfirmedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class InvestmentEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private JdbcTemplate jdbc;

    private InvestmentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new InvestmentEventListener(notificationService, emailService, jdbc);
    }

    @Test
    void onInvestmentConfirmed_createsNotificationAndSendsEmail() {
        var event = new InvestmentConfirmedEvent(42L, 7L, new BigDecimal("1500.00"), 100, "0xabc123");

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 7L))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, 42L))
            .thenReturn("inversor@test.com");

        listener.onInvestmentConfirmed(event);

        verify(notificationService).createNotification(
            eq(42L), eq("INVESTMENT_CONFIRMED"), eq("Inversión confirmada"),
            eq("Invertiste 1500.00 $IDEA en Mi Proyecto — recibiste 100 tokens."),
            eq("{\"proyectoId\":7,\"monto\":1500.00,\"cantidadSubTokens\":100,\"txHash\":\"0xabc123\"}")
        );
        verify(emailService).sendEmail(
            eq("inversor@test.com"), eq("Inversión en Mi Proyecto confirmada"),
            contains("1500.00")
        );
    }

    @Test
    void onInvestmentConfirmed_whenException_swallows() {
        var event = new InvestmentConfirmedEvent(42L, 7L, new BigDecimal("1500.00"), 100, "0xabc123");

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 7L))
            .thenReturn("Mi Proyecto");
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(42L), eq("INVESTMENT_CONFIRMED"), anyString(), anyString(), anyString()
            );

        // Must not throw
        listener.onInvestmentConfirmed(event);
    }
}
