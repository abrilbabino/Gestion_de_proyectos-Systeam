package com.systeam.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.notificaciones.event.ProjectStateChangedEvent;
import com.systeam.project.dto.CreateProjectRequest;
import com.systeam.project.dto.ProjectResponse;
import com.systeam.project.dto.UpdateProjectRequest;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.tokenization.service.TokenizationService;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TokenizationService tokenizationService;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProjectService projectService;

    private Proyecto proyectoBase;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, tokenizationService, jdbc, eventPublisher);
        Usuario creador = new Usuario();
        creador.setId(1L);

        proyectoBase = new Proyecto();
        proyectoBase.setId(1L);
        proyectoBase.setCreador(creador);
        proyectoBase.setTitulo("Proyecto Test");
        proyectoBase.setDescripcion("Descripcion de prueba");
        proyectoBase.setMontoRequerido(new BigDecimal("10000.00"));
        proyectoBase.setEstado("PREPARACION");
        proyectoBase.setGobernanzaComunidad(false);
        proyectoBase.setCreatedAt(LocalDateTime.now());
        proyectoBase.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void createProject_debeCrearProyectoConEstadoPreparacion() {
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        lenient().when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenReturn("SIM");

        CreateProjectRequest request = new CreateProjectRequest();
        request.setTitulo("Nuevo Proyecto");
        request.setSimbolo("NVP");
        request.setDescripcion("Descripcion del nuevo proyecto");
        request.setMontoRequerido(new BigDecimal("5000.00"));
        request.setGobernanzaComunidad(true);

        when(projectRepository.save(any(Proyecto.class))).thenAnswer(inv -> {
            Proyecto p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProjectResponse response = projectService.createProject(request, 1L);

        assertThat(response.getEstado()).isEqualTo("PREPARACION");
        assertThat(response.getTitulo()).isEqualTo("Nuevo Proyecto");
        assertThat(response.getMontoRequerido()).isEqualByComparingTo("5000.00");
        assertThat(response.getGobernanzaComunidad()).isTrue();
        assertThat(response.getCreadorId()).isEqualTo(1L);
    }

    @Test
    void createProject_gobernanzaComunitariaDefaultFalse() {
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        lenient().when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenReturn("SIM");

        CreateProjectRequest request = new CreateProjectRequest();
        request.setTitulo("Titulo");
        request.setSimbolo("TEST");
        request.setDescripcion("Descripcion");
        request.setMontoRequerido(new BigDecimal("1000.00"));

        when(projectRepository.save(any(Proyecto.class))).thenAnswer(inv -> {
            Proyecto p = inv.getArgument(0);
            p.setId(3L);
            return p;
        });

        ProjectResponse response = projectService.createProject(request, 1L);

        assertThat(response.getGobernanzaComunidad()).isFalse();
    }

    @Test
    void updateProject_cuandoEstadoEsPreparacion_debeActualizarCampos() {
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setTitulo("Titulo Actualizado");
        request.setMontoRequerido(new BigDecimal("20000.00"));

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        ProjectResponse response = projectService.updateProject(1L, request);

        assertThat(response).isNotNull();
        verify(projectRepository).save(any(Proyecto.class));
    }

    @Test
    void updateProject_cuandoEstadoNoEsPreparacion_debeLanzarConflict() {
        proyectoBase.setEstado("FINANCIAMIENTO");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setTitulo("Nuevo titulo");

        assertThatThrownBy(() -> projectService.updateProject(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FINANCIAMIENTO");
    }

    @Test
    void getProjectById_cuandoNoExiste_debeLanzarNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // FSM Regression: PREPARACION -> EN_AUDITORIA is now the only valid forward path from PREPARACION
    @Test
    void updateProjectStatus_transicionValidaPreparacionAEnAuditoria() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.updateProjectStatus(1L, "EN_AUDITORIA");

        verify(projectRepository).save(any(Proyecto.class));
        verify(eventPublisher).publishEvent(any(ProjectStateChangedEvent.class));
    }

    // FSM Regression: PREPARACION -> FINANCIAMIENTO is now INVALID (breaking change from HU-44)
    @Test
    void updateProjectStatus_transicionPreparacionAFinanciamientoAhoraInvalida() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));

        assertThatThrownBy(() -> projectService.updateProjectStatus(1L, "FINANCIAMIENTO"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("invalida");
    }

    @Test
    void updateProjectStatus_transicionEnAuditoriaAAuditado() {
        proyectoBase.setEstado("EN_AUDITORIA");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.updateProjectStatus(1L, "AUDITADO");

        verify(projectRepository).save(any(Proyecto.class));
        verify(eventPublisher).publishEvent(any(ProjectStateChangedEvent.class));
    }

    @Test
    void updateProjectStatus_transicionEnAuditoriaARechaazado() {
        proyectoBase.setEstado("EN_AUDITORIA");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.updateProjectStatus(1L, "RECHAZADO");

        verify(projectRepository).save(any(Proyecto.class));
        verify(eventPublisher).publishEvent(any(ProjectStateChangedEvent.class));
    }

    @Test
    void updateProjectStatus_rechazadoEsTerminal_noPudeTransicionar() {
        proyectoBase.setEstado("RECHAZADO");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));

        assertThatThrownBy(() -> projectService.updateProjectStatus(1L, "PREPARACION"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("invalida");
    }

    @Test
    void updateProjectStatus_transicionValidaAuditadoAFinanciamiento() throws Exception {
        proyectoBase.setEstado("AUDITADO");
        proyectoBase.setCupoMaximoTokens(1000);
        proyectoBase.setValorNominalToken(new BigDecimal("1.50"));

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.updateProjectStatus(1L, "FINANCIAMIENTO");

        verify(projectRepository).save(any(Proyecto.class));
        verify(tokenizationService).crearTokenParaProyecto(
            eq(proyectoBase.getId()), eq(proyectoBase.getTitulo()),
            any(),
            eq(proyectoBase.getCupoMaximoTokens()), eq(proyectoBase.getValorNominalToken()),
            eq(proyectoBase.getMontoRequerido()), any());
    }

    @Test
    void updateProjectStatus_transicionValidaFinanciamientoAEjecucion() {
        proyectoBase.setEstado("FINANCIAMIENTO");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.updateProjectStatus(1L, "EJECUCION");

        verify(projectRepository).save(any(Proyecto.class));
        verify(eventPublisher).publishEvent(any(ProjectStateChangedEvent.class));
    }

    @Test
    void updateProjectStatus_transicionInvalida_debeLanzarConflict() {
        proyectoBase.setEstado("EJECUCION");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));

        assertThatThrownBy(() -> projectService.updateProjectStatus(1L, "PREPARACION"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("invalida");
    }

    @Test
    void updateProjectStatus_finalizado_noPuedeTransicionar() {
        proyectoBase.setEstado("FINALIZADO");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));

        assertThatThrownBy(() -> projectService.updateProjectStatus(1L, "EJECUCION"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateProjectStatus_financiamiento_asignaPlazoSiNoTiene() throws Exception {
        // Must come from AUDITADO
        proyectoBase.setEstado("AUDITADO");
        proyectoBase.setCupoMaximoTokens(1000);
        proyectoBase.setValorNominalToken(new BigDecimal("1.50"));

        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoBase));
        when(projectRepository.save(any(Proyecto.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.updateProjectStatus(1L, "FINANCIAMIENTO");

        verify(projectRepository).save(any(Proyecto.class));
        verify(tokenizationService).crearTokenParaProyecto(
            eq(proyectoBase.getId()), eq(proyectoBase.getTitulo()),
            any(),
            eq(proyectoBase.getCupoMaximoTokens()), eq(proyectoBase.getValorNominalToken()),
            eq(proyectoBase.getMontoRequerido()), any());
    }

    @Test
    void evaluateAndUpdateStates_cuandoPlazoVencido_debeSetearFinalizado() {
        proyectoBase.setEstado("FINANCIAMIENTO");
        proyectoBase.setPlazo(LocalDateTime.now().minusDays(1));

        Proyecto proyectoActualizado = new Proyecto();
        proyectoActualizado.setId(1L);
        proyectoActualizado.setEstado("FINANCIAMIENTO");
        proyectoActualizado.setPlazo(LocalDateTime.now().minusDays(1));
        proyectoActualizado.setCreador(proyectoBase.getCreador());
        proyectoActualizado.setTitulo(proyectoBase.getTitulo());
        proyectoActualizado.setDescripcion(proyectoBase.getDescripcion());
        proyectoActualizado.setMontoRequerido(proyectoBase.getMontoRequerido());
        proyectoActualizado.setGobernanzaComunidad(proyectoBase.getGobernanzaComunidad());

        when(projectRepository.findProjectsInFinancing()).thenReturn(List.of(proyectoBase));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(proyectoActualizado));
        when(projectRepository.save(any(Proyecto.class))).thenReturn(proyectoBase);

        projectService.evaluateAndUpdateStates();

        verify(projectRepository).save(any(Proyecto.class));
    }

    @Test
    void evaluateAndUpdateStates_cuandoPlazoNoVencido_noDebeActualizar() {
        proyectoBase.setEstado("FINANCIAMIENTO");
        proyectoBase.setPlazo(LocalDateTime.now().plusDays(10));

        when(projectRepository.findProjectsInFinancing()).thenReturn(List.of(proyectoBase));

        projectService.evaluateAndUpdateStates();

        verify(projectRepository, org.mockito.Mockito.never()).save(any(Proyecto.class));
    }
}
