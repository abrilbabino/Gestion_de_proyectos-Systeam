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
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.DividendDistributedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class DividendEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private JdbcTemplate jdbc;

    private DividendEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DividendEventListener(notificationService, emailService, jdbc);
    }

    @Test
    void onDividendDistributed_createsNotificationAndSendsEmail() {
        var event = new DividendDistributedEvent(10L, 3L, new BigDecimal("250.75"));

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 10L))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, 3L))
            .thenReturn("beneficiario@test.com");

        listener.onDividendDistributed(event);

        verify(notificationService).createNotification(
            eq(3L), eq("DIVIDEND_DISTRIBUTED"), eq("Dividendos acreditados"),
            eq("Recibiste 250.75 IDEA en dividendos de Mi Proyecto."),
            eq("{\"proyectoId\":10,\"monto\":250.75}")
        );
        verify(emailService).sendEmail(
            eq("beneficiario@test.com"), eq("Dividendos de Mi Proyecto"),
            anyString()
        );
    }

    @Test
    void onDividendDistributed_whenException_swallows() {
        var event = new DividendDistributedEvent(10L, 3L, new BigDecimal("100.00"));

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 10L))
            .thenReturn("Mi Proyecto");
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(3L), eq("DIVIDEND_DISTRIBUTED"), anyString(), anyString(), anyString()
            );

        listener.onDividendDistributed(event);
    }
}
