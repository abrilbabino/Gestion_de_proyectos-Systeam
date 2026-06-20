package com.systeam.project.audit;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.systeam.notificaciones.event.ProjectAuditedEvent;
import com.systeam.notificaciones.event.ProjectRejectedEvent;
import com.systeam.project.audit.dto.AuditFindingRequest;
import com.systeam.project.audit.dto.AuditFindingResponse;
import com.systeam.project.audit.dto.ResultadoAuditoria;
import com.systeam.project.audit.repository.AuditFinding;
import com.systeam.project.audit.repository.JdbcAuditFindingRepository;
import com.systeam.project.exception.InvalidStateTransitionException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.project.service.ProjectService;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

/**
 * Unit tests for AuditService — U1 through U8.
 * No Spring context: all collaborators are mocked via Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectService projectService;
    @Mock private JdbcAuditFindingRepository findingRepository;
    @Mock private AuditOracleClient oracleClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuditService auditService;

    private static final Long PROYECTO_ID = 1L;
    private static final Long AUDITOR_ID  = 99L;
    private static final Long CREADOR_ID  = 42L;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(
            projectRepository, projectService, findingRepository, oracleClient, eventPublisher
        );
    }

    // Helpers

    private Proyecto buildProyecto(String estado) {
        Usuario creador = new Usuario();
        creador.setId(CREADOR_ID);

        Proyecto p = new Proyecto();
        p.setId(PROYECTO_ID);
        p.setEstado(estado);
        p.setCreador(creador);
        return p;
    }

    private AuditFindingRequest buildRequest(ResultadoAuditoria resultado) {
        AuditFindingRequest req = new AuditFindingRequest();
        req.setKybUrl("https://kyb.example.com/doc.pdf");
        req.setResultado(resultado);
        req.setObservaciones("Observaciones de prueba");
        return req;
    }

    private AuditFinding savedFinding(Long id, ResultadoAuditoria resultado, String txHash) {
        AuditFinding f = new AuditFinding();
        f.setId(id);
        f.setProyectoId(PROYECTO_ID);
        f.setAuditorId(AUDITOR_ID);
        f.setKybUrl("https://kyb.example.com/doc.pdf");
        f.setResultado(resultado.name());
        f.setObservaciones("Observaciones de prueba");
        f.setTxHash(txHash);
        f.setCreatedAt(java.time.OffsetDateTime.now());
        return f;
    }

    // U1: submitFinding — APROBADO path — oracle succeeds, ProjectAuditedEvent published
    @Test
    void u1_submitFinding_aprobado_oracleSucceeds() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("EN_AUDITORIA")));
        when(oracleClient.submitAuditFinding(any(), any(), any(), any())).thenReturn("0xABC");
        when(findingRepository.save(any())).thenReturn(savedFinding(1L, ResultadoAuditoria.APROBADO, "0xABC"));

        AuditFindingResponse response = auditService.submitFinding(
            PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO)
        );

        assertThat(response.getResultado()).isEqualTo("APROBADO");
        assertThat(response.getTxHash()).isEqualTo("0xABC");
        verify(projectService).updateProjectStatus(PROYECTO_ID, "AUDITADO");
        verify(eventPublisher).publishEvent(any(ProjectAuditedEvent.class));
    }

    // U2: submitFinding — RECHAZADO path — oracle succeeds, event published
    @Test
    void u2_submitFinding_rechazado_eventPublished() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("EN_AUDITORIA")));
        when(oracleClient.submitAuditFinding(any(), any(), any(), any())).thenReturn("0xDEF");
        when(findingRepository.save(any())).thenReturn(savedFinding(2L, ResultadoAuditoria.RECHAZADO, "0xDEF"));

        AuditFindingResponse response = auditService.submitFinding(
            PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.RECHAZADO)
        );

        assertThat(response.getResultado()).isEqualTo("RECHAZADO");
        verify(projectService).updateProjectStatus(PROYECTO_ID, "RECHAZADO");
        verify(eventPublisher).publishEvent(any(ProjectRejectedEvent.class));
    }

    // U3: oracle throws — finding persisted with tx_hash=null, no exception propagated
    @Test
    void u3_submitFinding_oracleThrows_findingPersistedWithNullTxHash() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("EN_AUDITORIA")));
        when(oracleClient.submitAuditFinding(any(), any(), any(), any())).thenReturn(null);
        when(findingRepository.save(any())).thenReturn(savedFinding(3L, ResultadoAuditoria.APROBADO, null));

        AuditFindingResponse response = auditService.submitFinding(
            PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO)
        );

        assertThat(response.getTxHash()).isNull();
        verify(findingRepository).save(any());
        // No exception must escape
    }

    // U4: project not in EN_AUDITORIA — throws InvalidStateTransitionException
    @Test
    void u4_submitFinding_wrongState_throwsInvalidStateTransition() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("PREPARACION")));

        assertThatThrownBy(() ->
            auditService.submitFinding(PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO))
        ).isInstanceOf(InvalidStateTransitionException.class);

        verify(findingRepository, never()).save(any());
        verify(oracleClient, never()).submitAuditFinding(any(), any(), any(), any());
    }

    // U5: project not found — throws ResourceNotFoundException
    @Test
    void u5_submitFinding_projectNotFound_throwsResourceNotFound() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            auditService.submitFinding(PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO))
        ).isInstanceOf(ResourceNotFoundException.class)
         .hasMessageContaining(String.valueOf(PROYECTO_ID));
    }

    // U6: zero-address oracle returns null — tx_hash=null, no oracle call needed check
    @Test
    void u6_submitFinding_oracleReturnsNull_txHashNull() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("EN_AUDITORIA")));
        when(oracleClient.submitAuditFinding(any(), any(), any(), any())).thenReturn(null);
        when(findingRepository.save(any())).thenReturn(savedFinding(4L, ResultadoAuditoria.APROBADO, null));

        AuditFindingResponse response = auditService.submitFinding(
            PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO)
        );

        assertThat(response.getTxHash()).isNull();
    }

    // U7: listFindings returns list ordered by createdAt DESC
    @Test
    void u7_listFindings_returnsList() {
        AuditFinding f1 = savedFinding(1L, ResultadoAuditoria.APROBADO, "0x1");
        AuditFinding f2 = savedFinding(2L, ResultadoAuditoria.RECHAZADO, "0x2");
        when(findingRepository.findByProyectoIdOrderByCreatedAtDesc(PROYECTO_ID))
            .thenReturn(List.of(f2, f1));

        List<AuditFindingResponse> result = auditService.listFindings(PROYECTO_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(1).getId()).isEqualTo(1L);
    }

    // U8: project in RECHAZADO state — throws InvalidStateTransitionException (terminal)
    @Test
    void u8_submitFinding_rechazadoProject_throwsInvalidStateTransition() {
        when(projectRepository.findById(PROYECTO_ID)).thenReturn(Optional.of(buildProyecto("RECHAZADO")));

        assertThatThrownBy(() ->
            auditService.submitFinding(PROYECTO_ID, AUDITOR_ID, buildRequest(ResultadoAuditoria.APROBADO))
        ).isInstanceOf(InvalidStateTransitionException.class);

        verify(findingRepository, never()).save(any());
    }
}
