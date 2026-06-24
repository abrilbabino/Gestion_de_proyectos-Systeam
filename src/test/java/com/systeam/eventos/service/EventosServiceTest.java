package com.systeam.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.systeam.eventos.dto.AsistenciaResponse;
import com.systeam.eventos.dto.EventoRequest;
import com.systeam.eventos.dto.EventoResponse;
import com.systeam.eventos.repository.EventosRepository;
import com.systeam.notificaciones.event.EventRewardedEvent;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.rewards.service.RewardService;

@ExtendWith(MockitoExtension.class)
class EventosServiceTest {

    @Mock private EventosRepository eventosRepository;
    @Mock private RewardService rewardService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private EventosService service;

    @BeforeEach
    void setUp() {
        service = new EventosService(eventosRepository, rewardService, eventPublisher);
    }

    // ---- createEvento ----

    @Test
    void createEvento_persistsAndReturnsResponse() {
        EventoRequest request = buildRequest();
        EventoResponse expected = buildResponse(1L);
        when(eventosRepository.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(eventosRepository.findById(1L)).thenReturn(expected);

        EventoResponse result = service.createEvento(request, 99L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(eventosRepository).insert(
                eq("Demo Event"), eq("A demo"), any(), eq(new BigDecimal("20")), eq(5L), eq(99L), any());
    }

    // ---- getEvento ----

    @Test
    void getEvento_existingId_returnsResponse() {
        EventoResponse expected = buildResponse(1L);
        when(eventosRepository.findById(1L)).thenReturn(expected);

        EventoResponse result = service.getEvento(1L);

        assertThat(result.getTitulo()).isEqualTo("Demo Event");
    }

    @Test
    void getEvento_missingId_throwsNotFound() {
        when(eventosRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getEvento(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ---- confirmAttendance — full lifecycle ----

    @Test
    void confirmAttendance_firstTime_insertsRewardAndPublishesEvent() {
        EventoResponse evento = buildResponse(1L);
        evento.setRewardAmount(new BigDecimal("20"));
        when(eventosRepository.findById(1L)).thenReturn(evento);
        when(eventosRepository.insertAsistencia(1L, 42L)).thenReturn(true);
        when(rewardService.accrue(anyLong(), anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(true);

        AsistenciaResponse asistencia = new AsistenciaResponse();
        asistencia.setId(10L);
        asistencia.setEventoId(1L);
        asistencia.setUserId(42L);
        asistencia.setConfirmedAt(LocalDateTime.now());
        when(eventosRepository.findAsistenciasByEvento(1L)).thenReturn(List.of(asistencia));

        AsistenciaResponse result = service.confirmAttendance(1L, 42L);

        assertThat(result.getUserId()).isEqualTo(42L);
        verify(rewardService).accrue(42L, "EVENT_ATTENDANCE", "EVENTO", 1L, null, new BigDecimal("20"));

        ArgumentCaptor<EventRewardedEvent> captor = ArgumentCaptor.forClass(EventRewardedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EventRewardedEvent published = captor.getValue();
        assertThat(published.getUserId()).isEqualTo(42L);
        assertThat(published.getEventoId()).isEqualTo(1L);
        assertThat(published.getReward()).isEqualByComparingTo(new BigDecimal("20"));
    }

    // ---- confirmAttendance — duplicate blocked ----

    @Test
    void confirmAttendance_duplicate_throwsConflict() {
        EventoResponse evento = buildResponse(1L);
        when(eventosRepository.findById(1L)).thenReturn(evento);
        when(eventosRepository.insertAsistencia(1L, 42L)).thenReturn(false);

        assertThatThrownBy(() -> service.confirmAttendance(1L, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya registró asistencia");

        verify(rewardService, never()).accrue(anyLong(), anyString(), anyString(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- confirmAttendance — event not found ----

    @Test
    void confirmAttendance_eventNotFound_throwsNotFound() {
        when(eventosRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.confirmAttendance(999L, 42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ---- confirmAttendance — zero reward skips accrual ----

    @Test
    void confirmAttendance_zeroReward_skipsAccrual() {
        EventoResponse evento = buildResponse(1L);
        evento.setRewardAmount(BigDecimal.ZERO);
        when(eventosRepository.findById(1L)).thenReturn(evento);
        when(eventosRepository.insertAsistencia(1L, 42L)).thenReturn(true);

        AsistenciaResponse asistencia = new AsistenciaResponse();
        asistencia.setId(10L);
        asistencia.setEventoId(1L);
        asistencia.setUserId(42L);
        asistencia.setConfirmedAt(LocalDateTime.now());
        when(eventosRepository.findAsistenciasByEvento(1L)).thenReturn(List.of(asistencia));

        AsistenciaResponse result = service.confirmAttendance(1L, 42L);

        assertThat(result).isNotNull();
        verify(rewardService, never()).accrue(anyLong(), anyString(), anyString(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- deleteEvento ----

    @Test
    void deleteEvento_existingId_deletes() {
        EventoResponse existing = buildResponse(1L);
        when(eventosRepository.findById(1L)).thenReturn(existing);

        service.deleteEvento(1L);

        verify(eventosRepository).deleteById(1L);
    }

    @Test
    void deleteEvento_missingId_throwsNotFound() {
        when(eventosRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteEvento(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- helpers ----

    private EventoRequest buildRequest() {
        EventoRequest r = new EventoRequest();
        r.setTitulo("Demo Event");
        r.setDescripcion("A demo");
        r.setFechaEvento(LocalDateTime.of(2026, 7, 1, 10, 0));
        r.setRewardAmount(new BigDecimal("20"));
        r.setProyectoId(5L);
        return r;
    }

    private EventoResponse buildResponse(Long id) {
        EventoResponse r = new EventoResponse();
        r.setId(id);
        r.setTitulo("Demo Event");
        r.setDescripcion("A demo");
        r.setFechaEvento(LocalDateTime.of(2026, 7, 1, 10, 0));
        r.setRewardAmount(new BigDecimal("20"));
        r.setProyectoId(5L);
        r.setCreatedBy(99L);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }
}
