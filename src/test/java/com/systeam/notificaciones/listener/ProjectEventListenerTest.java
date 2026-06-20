package com.systeam.notificaciones.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.ProjectAuditedEvent;
import com.systeam.notificaciones.event.ProjectStateChangedEvent;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.notificaciones.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class ProjectEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private JdbcTemplate jdbc;

    private ProjectEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProjectEventListener(notificationService, emailService, jdbc);
    }

    @Test
    void onProjectStateChanged_createsNotification_noEmail() {
        var event = new ProjectStateChangedEvent(10L, "EN_REVISION", "APROBADO", 5L);

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 10L))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT creador_id FROM projects WHERE id = ?", Long.class, 10L))
            .thenReturn(5L);

        listener.onProjectStateChanged(event);

        verify(notificationService).createNotification(
            eq(5L), eq("PROJECT_STATE_CHANGED"), eq("Mi Proyecto cambió de estado"),
            eq("Tu proyecto pasó de EN_REVISION a APROBADO."),
            eq("{\"proyectoId\":10,\"fromState\":\"EN_REVISION\",\"toState\":\"APROBADO\"}")
        );
        verifyNoInteractions(emailService);
    }

    @Test
    void onProjectStateChanged_whenException_swallows() {
        var event = new ProjectStateChangedEvent(10L, "EN_REVISION", "APROBADO", 5L);

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 10L))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT creador_id FROM projects WHERE id = ?", Long.class, 10L))
            .thenReturn(5L);
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(5L), eq("PROJECT_STATE_CHANGED"), anyString(), anyString(), anyString()
            );

        listener.onProjectStateChanged(event);
    }

    @Test
    void onProjectAudited_approved_createsNotificationAndSendsEmail() {
        Long proyectoId = 10L;
        Long creadorId = 7L;
        var event = new ProjectAuditedEvent(proyectoId, 5L, ProjectAuditedEvent.Result.APPROVED, 77L);

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, proyectoId))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT creador_id FROM projects WHERE id = ?", Long.class, proyectoId))
            .thenReturn(creadorId);
        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, creadorId))
            .thenReturn("creador@test.com");

        listener.onProjectAudited(event);

        verify(notificationService).createNotification(
            eq(creadorId), eq("PROJECT_AUDITED"), eq("Proyecto auditado"),
            eq("Mi Proyecto ha sido aprobado en la auditoría."),
            eq("{\"proyectoId\":10,\"auditorId\":5,\"result\":\"APPROVED\",\"findingId\":77}")
        );
        verify(emailService).sendEmail(
            eq("creador@test.com"), eq("Resultado de auditoría — Mi Proyecto"),
            anyString()
        );
    }

    @Test
    void onProjectAudited_rejected_createsNotificationAndSendsEmail() {
        Long proyectoId = 10L;
        Long creadorId = 7L;
        var event = new ProjectAuditedEvent(proyectoId, 5L, ProjectAuditedEvent.Result.REJECTED, 77L);

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, proyectoId))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT creador_id FROM projects WHERE id = ?", Long.class, proyectoId))
            .thenReturn(creadorId);
        when(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, creadorId))
            .thenReturn("creador@test.com");

        listener.onProjectAudited(event);

        verify(notificationService).createNotification(
            eq(creadorId), eq("PROJECT_AUDITED"), eq("Proyecto auditado"),
            eq("Mi Proyecto ha sido rechazado en la auditoría."),
            eq("{\"proyectoId\":10,\"auditorId\":5,\"result\":\"REJECTED\",\"findingId\":77}")
        );
        verify(emailService).sendEmail(
            eq("creador@test.com"), eq("Resultado de auditoría — Mi Proyecto"),
            anyString()
        );
    }

    @Test
    void onProjectAudited_whenException_swallows() {
        var event = new ProjectAuditedEvent(10L, 5L, ProjectAuditedEvent.Result.APPROVED, 77L);

        when(jdbc.queryForObject("SELECT titulo FROM projects WHERE id = ?", String.class, 10L))
            .thenReturn("Mi Proyecto");
        when(jdbc.queryForObject("SELECT creador_id FROM projects WHERE id = ?", Long.class, 10L))
            .thenReturn(7L);
        doThrow(new RuntimeException("DB error"))
            .when(notificationService).createNotification(
                eq(7L), eq("PROJECT_AUDITED"), anyString(), anyString(), anyString()
            );

        listener.onProjectAudited(event);
    }
}
