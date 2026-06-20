package com.systeam.notificaciones.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.GovernanceProposalEvent;
import com.systeam.notificaciones.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class GovernanceEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private JdbcTemplate jdbc;

    private GovernanceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new GovernanceEventListener(notificationService, jdbc);
    }

    @Test
    void onGovernanceProposal_created_createsNotification() {
        var event = new GovernanceProposalEvent(99L, 3L, GovernanceProposalEvent.Action.CREATED);

        when(jdbc.queryForObject(
            "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
            String.class, 99L)).thenReturn("Mi Propuesta");

        listener.onGovernanceProposal(event);

        verify(notificationService).createNotification(
            eq(3L), eq("GOVERNANCE_PROPOSAL"), eq("Propuesta de gobernanza"),
            eq("Propuesta \"Mi Propuesta\" creada."),
            eq("{\"proposalId\":99,\"action\":\"CREATED\"}")
        );
    }

    @Test
    void onGovernanceProposal_executed_createsNotification() {
        var event = new GovernanceProposalEvent(99L, 3L, GovernanceProposalEvent.Action.EXECUTED);

        when(jdbc.queryForObject(
            "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
            String.class, 99L)).thenReturn("Mi Propuesta");

        listener.onGovernanceProposal(event);

        verify(notificationService).createNotification(
            eq(3L), eq("GOVERNANCE_PROPOSAL"), eq("Propuesta de gobernanza"),
            eq("Propuesta \"Mi Propuesta\" ejecutada."),
            eq("{\"proposalId\":99,\"action\":\"EXECUTED\"}")
        );
    }

    @Test
    void onGovernanceProposal_cancelled_createsNotification() {
        var event = new GovernanceProposalEvent(99L, 3L, GovernanceProposalEvent.Action.CANCELLED);

        when(jdbc.queryForObject(
            "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
            String.class, 99L)).thenReturn("Mi Propuesta");

        listener.onGovernanceProposal(event);

        verify(notificationService).createNotification(
            eq(3L), eq("GOVERNANCE_PROPOSAL"), eq("Propuesta de gobernanza"),
            eq("Propuesta \"Mi Propuesta\" cancelada."),
            eq("{\"proposalId\":99,\"action\":\"CANCELLED\"}")
        );
    }

    @Test
    void onGovernanceProposal_whenException_swallows() {
        var event = new GovernanceProposalEvent(99L, 3L, GovernanceProposalEvent.Action.CREATED);

        when(jdbc.queryForObject(
            "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
            String.class, 99L)).thenReturn("Mi Propuesta");
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(3L), eq("GOVERNANCE_PROPOSAL"), anyString(), anyString(), anyString()
            );

        listener.onGovernanceProposal(event);
    }
}
