package com.systeam.notificaciones.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.MarketplaceEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class MarketplaceEventListenerTest {

    @Mock NotificationService notificationService;
    @Mock EmailService emailService;
    @Mock JdbcTemplate jdbc;

    private MarketplaceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MarketplaceEventListener(notificationService, emailService, jdbc);
    }

    @Test
    void onMarketplaceEvent_listed_createsNotification_noEmail() {
        var event = new MarketplaceEvent(10L, null, 8L, MarketplaceEvent.Type.LISTED,
            "Proyecto X", BigInteger.valueOf(100), new BigInteger("1000000000000000000"), "0xlist");

        listener.onMarketplaceEvent(event);

        verify(notificationService).createNotification(
            eq(8L), eq("MARKETPLACE_LISTED"), eq("Listado en marketplace"),
            eq("Tu proyecto Proyecto X ha sido listado en el marketplace."),
            anyString()
        );
        verifyNoInteractions(emailService);
    }

    @Test
    void onMarketplaceEvent_sold_createsNotificationAndSendsEmailToBoth() {
        var event = new MarketplaceEvent(10L, 2L, 8L, MarketplaceEvent.Type.SOLD,
            "Proyecto X", BigInteger.valueOf(50), new BigInteger("2000000000000000000"), "0xsold123");

        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, 2L))
            .thenReturn("buyer@test.com");
        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, 8L))
            .thenReturn("seller@test.com");

        listener.onMarketplaceEvent(event);

        // Buyer notification
        verify(notificationService).createNotification(
            eq(2L), eq("MARKETPLACE_SOLD"), eq("Compra en marketplace"),
            eq("Has comprado 50 tokens de Proyecto X por 100 $IDEA."),
            anyString()
        );
        // Seller notification
        verify(notificationService).createNotification(
            eq(8L), eq("MARKETPLACE_SOLD"), eq("Venta en marketplace"),
            eq("Has vendido 50 tokens de Proyecto X por 100 $IDEA."),
            anyString()
        );
        // Email to buyer
        verify(emailService).sendEmail(
            eq("buyer@test.com"), eq("Compra en marketplace — Proyecto X"),
            anyString()
        );
        // Email to seller
        verify(emailService).sendEmail(
            eq("seller@test.com"), eq("Venta en marketplace — Proyecto X"),
            anyString()
        );
    }

    @Test
    void onMarketplaceEvent_cancelled_createsNotification_noEmail() {
        var event = new MarketplaceEvent(10L, null, 8L, MarketplaceEvent.Type.CANCELLED,
            "Proyecto X", BigInteger.valueOf(100), null, null);

        listener.onMarketplaceEvent(event);

        verify(notificationService).createNotification(
            eq(8L), eq("MARKETPLACE_CANCELLED"), eq("Listado cancelado"),
            eq("El listado de Proyecto X ha sido cancelado."),
            anyString()
        );
        verifyNoInteractions(emailService);
    }

    @Test
    void onMarketplaceEvent_whenException_swallows() {
        var event = new MarketplaceEvent(10L, 2L, 8L, MarketplaceEvent.Type.SOLD,
            "Proyecto X", BigInteger.valueOf(50), new BigInteger("2000000000000000000"), "0xsold456");

        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(2L), eq("MARKETPLACE_SOLD"), anyString(), anyString(), anyString()
            );

        listener.onMarketplaceEvent(event);
    }
}
