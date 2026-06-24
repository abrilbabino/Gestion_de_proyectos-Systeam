package com.systeam.eventos.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.eventos.dto.AsistenciaResponse;
import com.systeam.eventos.dto.EventoRequest;
import com.systeam.eventos.dto.EventoResponse;
import com.systeam.eventos.repository.EventosRepository;
import com.systeam.notificaciones.event.EventRewardedEvent;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.rewards.service.RewardService;

@Service
public class EventosService {

    private static final Logger log = LoggerFactory.getLogger(EventosService.class);

    private final EventosRepository eventosRepository;
    private final RewardService rewardService;
    private final ApplicationEventPublisher eventPublisher;

    public EventosService(EventosRepository eventosRepository,
                          RewardService rewardService,
                          ApplicationEventPublisher eventPublisher) {
        this.eventosRepository = eventosRepository;
        this.rewardService = rewardService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EventoResponse createEvento(EventoRequest request, Long createdBy) {
        Long id = eventosRepository.insert(
                request.getTitulo(),
                request.getDescripcion(),
                request.getFechaEvento(),
                request.getRewardAmount(),
                request.getProyectoId(),
                createdBy,
                request.getCronograma());
        log.info("Evento created: id={} titulo={} by userId={}", id, request.getTitulo(), createdBy);
        return eventosRepository.findById(id);
    }

    public EventoResponse getEvento(Long id) {
        EventoResponse evento = eventosRepository.findById(id);
        if (evento == null) {
            throw new ResourceNotFoundException("Evento no encontrado: " + id);
        }
        return evento;
    }

    public List<EventoResponse> listEventos() {
        return eventosRepository.findAll();
    }

    @Transactional
    public EventoResponse updateEvento(Long id, EventoRequest request) {
        EventoResponse existing = eventosRepository.findById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("Evento no encontrado: " + id);
        }
        eventosRepository.update(id,
                request.getTitulo(),
                request.getDescripcion(),
                request.getFechaEvento(),
                request.getRewardAmount(),
                request.getProyectoId(),
                request.getCronograma());
        log.info("Evento updated: id={}", id);
        return eventosRepository.findById(id);
    }

    @Transactional
    public void deleteEvento(Long id) {
        EventoResponse existing = eventosRepository.findById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("Evento no encontrado: " + id);
        }
        eventosRepository.deleteById(id);
        log.info("Evento deleted: id={}", id);
    }

    /**
     * Confirms attendance for a user at an event.
     * Within a single transaction:
     * 1. Validates the event exists
     * 2. Inserts attendance (idempotent — duplicate returns ConflictException)
     * 3. Accrues the event's reward to the attendee via RewardService
     * 4. Publishes EventRewardedEvent for notification listeners
     */
    @Transactional
    public AsistenciaResponse confirmAttendance(Long eventoId, Long userId) {
        EventoResponse evento = eventosRepository.findById(eventoId);
        if (evento == null) {
            throw new ResourceNotFoundException("Evento no encontrado: " + eventoId);
        }

        boolean inserted = eventosRepository.insertAsistencia(eventoId, userId);
        if (!inserted) {
            throw new ConflictException("El usuario ya registró asistencia a este evento");
        }

        // Accrue reward if the event has a reward amount > 0
        if (evento.getRewardAmount() != null && evento.getRewardAmount().signum() > 0) {
            rewardService.accrue(userId, "EVENT_ATTENDANCE", "EVENTO", eventoId, null,
                    evento.getRewardAmount());
            eventPublisher.publishEvent(new EventRewardedEvent(userId, eventoId, evento.getRewardAmount()));
        }

        log.info("Attendance confirmed: eventoId={} userId={} reward={}",
                eventoId, userId, evento.getRewardAmount());

        // Return the latest attendance record
        List<AsistenciaResponse> asistencias = eventosRepository.findAsistenciasByEvento(eventoId);
        return asistencias.stream()
                .filter(a -> a.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Asistencia insertada pero no encontrada"));
    }

    public List<AsistenciaResponse> listAsistencias(Long eventoId) {
        EventoResponse evento = eventosRepository.findById(eventoId);
        if (evento == null) {
            throw new ResourceNotFoundException("Evento no encontrado: " + eventoId);
        }
        return eventosRepository.findAsistenciasByEvento(eventoId);
    }
}
