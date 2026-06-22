package com.systeam.eventos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.eventos.dto.AsistenciaRequest;
import com.systeam.eventos.dto.AsistenciaResponse;
import com.systeam.eventos.dto.EventoRequest;
import com.systeam.eventos.dto.EventoResponse;
import com.systeam.eventos.service.EventosService;
import com.systeam.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventosController.class)
@Import(EventosControllerTest.TestSecurityConfig.class)
class EventosControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EventosService eventosService;

    @BeforeEach
    void setup() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    new JwtPrincipal(1L, "test@test.com"),
                    auth.getCredentials(),
                    auth.getAuthorities()
                )
            );
        }
    }

    // ---- POST /api/eventos (create) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEvento_admin_retorna201() throws Exception {
        EventoResponse response = buildEventoResponse();
        when(eventosService.createEvento(any(), anyLong())).thenReturn(response);

        mockMvc.perform(post("/api/eventos")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildEventoRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.titulo").value("Demo Event"));
    }

    @Test
    @WithMockUser
    void createEvento_nonAdmin_retorna403() throws Exception {
        mockMvc.perform(post("/api/eventos")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildEventoRequest())))
            .andExpect(status().isForbidden());
    }

    // ---- GET /api/eventos (list) ----

    @Test
    @WithMockUser(authorities = "evento:read")
    void listEventos_conPermiso_retorna200() throws Exception {
        when(eventosService.listEventos()).thenReturn(List.of(buildEventoResponse()));

        mockMvc.perform(get("/api/eventos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].titulo").value("Demo Event"));
    }

    @Test
    @WithMockUser
    void listEventos_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/eventos"))
            .andExpect(status().isForbidden());
    }

    // ---- GET /api/eventos/{id} (detail) ----

    @Test
    @WithMockUser(authorities = "evento:read")
    void getEvento_conPermiso_retorna200() throws Exception {
        when(eventosService.getEvento(1L)).thenReturn(buildEventoResponse());

        mockMvc.perform(get("/api/eventos/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    // ---- PUT /api/eventos/{id} (update) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEvento_admin_retorna200() throws Exception {
        when(eventosService.updateEvento(anyLong(), any())).thenReturn(buildEventoResponse());

        mockMvc.perform(put("/api/eventos/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildEventoRequest())))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void updateEvento_nonAdmin_retorna403() throws Exception {
        mockMvc.perform(put("/api/eventos/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildEventoRequest())))
            .andExpect(status().isForbidden());
    }

    // ---- DELETE /api/eventos/{id} ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteEvento_admin_retorna204() throws Exception {
        mockMvc.perform(delete("/api/eventos/1")
                .with(csrf()))
            .andExpect(status().isNoContent());

        verify(eventosService).deleteEvento(1L);
    }

    @Test
    @WithMockUser
    void deleteEvento_nonAdmin_retorna403() throws Exception {
        mockMvc.perform(delete("/api/eventos/1")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    // ---- POST /api/eventos/{id}/asistencia (attendance) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAttendance_admin_retorna201() throws Exception {
        AsistenciaResponse asistencia = new AsistenciaResponse();
        asistencia.setId(10L);
        asistencia.setEventoId(1L);
        asistencia.setUserId(42L);
        asistencia.setConfirmedAt(LocalDateTime.now());
        when(eventosService.confirmAttendance(1L, 42L)).thenReturn(asistencia);

        AsistenciaRequest request = new AsistenciaRequest();
        request.setUserId(42L);

        mockMvc.perform(post("/api/eventos/1/asistencia")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(42));
    }

    @Test
    @WithMockUser
    void confirmAttendance_nonAdmin_retorna403() throws Exception {
        AsistenciaRequest request = new AsistenciaRequest();
        request.setUserId(42L);

        mockMvc.perform(post("/api/eventos/1/asistencia")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    // ---- GET /api/eventos/{id}/asistencias (list attendees) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAsistencias_admin_retorna200() throws Exception {
        when(eventosService.listAsistencias(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/eventos/1/asistencias"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void listAsistencias_nonAdmin_retorna403() throws Exception {
        mockMvc.perform(get("/api/eventos/1/asistencias"))
            .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private EventoRequest buildEventoRequest() {
        EventoRequest r = new EventoRequest();
        r.setTitulo("Demo Event");
        r.setDescripcion("A demo");
        r.setFechaEvento(LocalDateTime.of(2026, 7, 1, 10, 0));
        r.setRewardAmount(new BigDecimal("20"));
        r.setProyectoId(5L);
        return r;
    }

    private EventoResponse buildEventoResponse() {
        EventoResponse r = new EventoResponse();
        r.setId(1L);
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
