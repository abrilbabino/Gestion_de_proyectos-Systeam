package com.systeam.eventos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.eventos.dto.AsistenciaRequest;
import com.systeam.eventos.dto.AsistenciaResponse;
import com.systeam.eventos.dto.EventoRequest;
import com.systeam.eventos.dto.EventoResponse;
import com.systeam.eventos.service.EventosService;
import com.systeam.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/eventos")
public class EventosController {

    private final EventosService eventosService;

    public EventosController(EventosService eventosService) {
        this.eventosService = eventosService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EventoResponse createEvento(@RequestBody @Valid EventoRequest request,
                                       @AuthenticationPrincipal JwtPrincipal user) {
        return eventosService.createEvento(request, user.userId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('evento:read')")
    public List<EventoResponse> listEventos() {
        return eventosService.listEventos();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('evento:read')")
    public EventoResponse getEvento(@PathVariable Long id) {
        return eventosService.getEvento(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventoResponse updateEvento(@PathVariable Long id,
                                       @RequestBody @Valid EventoRequest request) {
        return eventosService.updateEvento(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteEvento(@PathVariable Long id) {
        eventosService.deleteEvento(id);
    }

    @PostMapping("/{id}/asistencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AsistenciaResponse confirmAttendance(@PathVariable Long id,
                                                @RequestBody @Valid AsistenciaRequest request) {
        return eventosService.confirmAttendance(id, request.getUserId());
    }

    @GetMapping("/{id}/asistencias")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AsistenciaResponse> listAsistencias(@PathVariable Long id) {
        return eventosService.listAsistencias(id);
    }
}
