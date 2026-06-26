package com.systeam.project.audit;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AuditService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final JdbcAuditFindingRepository findingRepository;
    private final AuditOracleClient oracleClient;
    private final ApplicationEventPublisher eventPublisher;

    public AuditService(ProjectRepository projectRepository,
                        ProjectService projectService,
                        JdbcAuditFindingRepository findingRepository,
                        AuditOracleClient oracleClient,
                        ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.projectService    = projectService;
        this.findingRepository = findingRepository;
        this.oracleClient      = oracleClient;
        this.eventPublisher    = eventPublisher;
    }

    /**
     * Submits an audit finding for a project in EN_AUDITORIA state.
     * Steps:
     *   1. Load project — throw ResourceNotFoundException if absent
     *   2. Assert estado = EN_AUDITORIA — throw InvalidStateTransitionException if not
     *   3. Call oracle (graceful degradation: null on failure)
     *   4. Persist finding
     *   5. Transition project state (FINANCIAMIENTO or RECHAZADO)
     *   6. Publish ProjectRejectedEvent on RECHAZADO
     */
    @Transactional
    public AuditFindingResponse submitFinding(Long proyectoId, Long auditorId, AuditFindingRequest request) {
        // 1. Load project
        Proyecto proyecto = projectRepository.findById(proyectoId)
            .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + proyectoId));

        // 2. Assert state
        if (!"EN_AUDITORIA".equals(proyecto.getEstado())) {
            throw new InvalidStateTransitionException(proyecto.getEstado(), "EN_AUDITORIA");
        }

        // 3. Oracle call — degrades to null. Pack scores in observaciones as JSON
        String packedObservaciones = String.format(
            "{\"riskScore\":\"%s\",\"viability\":%d,\"observaciones\":\"%s\"}",
            request.getRiskScore() != null ? request.getRiskScore() : "",
            request.getFinancialViabilityScore() != null ? request.getFinancialViabilityScore() : 0,
            request.getObservaciones() != null ? request.getObservaciones() : ""
        );
        String txHash = oracleClient.submitAuditFinding(
            proyectoId,
            request.getResultado(),
            packedObservaciones,
            "https://systeam.com/kyb/not-required"
        );

        // 4. Persist finding
        AuditFinding finding = new AuditFinding();
        finding.setProyectoId(proyectoId);
        finding.setAuditorId(auditorId);
        finding.setKybUrl("https://systeam.com/kyb/not-required");
        finding.setResultado(request.getResultado().name());
        finding.setObservaciones(request.getObservaciones());
        finding.setRiskScore(request.getRiskScore());
        finding.setFinancialViabilityScore(request.getFinancialViabilityScore());
        finding.setTxHash(txHash);

        AuditFinding saved = findingRepository.save(finding);

        // 5. Transition project state
        String newEstado;
        if (request.getResultado() == ResultadoAuditoria.APROBADO) {
            newEstado = "AUDITADO";
        } else if (request.getResultado() == ResultadoAuditoria.NECESITA_CAMBIOS) {
            newEstado = "PREPARACION";
        } else {
            newEstado = "RECHAZADO";
        }
        projectService.updateProjectStatus(proyectoId, newEstado);

        // 6. Publish event
        if (request.getResultado() == ResultadoAuditoria.APROBADO) {
            eventPublisher.publishEvent(
                new ProjectAuditedEvent(proyectoId, auditorId, ProjectAuditedEvent.Result.APPROVED, saved.getId())
            );
        } else if (request.getResultado() == ResultadoAuditoria.RECHAZADO) {
            Long creadorId = proyecto.getCreador() != null ? proyecto.getCreador().getId() : null;
            eventPublisher.publishEvent(
                new ProjectRejectedEvent(proyectoId, auditorId, saved.getId(), creadorId)
            );
        } else if (request.getResultado() == ResultadoAuditoria.NECESITA_CAMBIOS) {
            // Option to publish a ProjectNeedsChangesEvent if desired in the future
        }

        return toResponse(saved);
    }

    /**
     * Lists all audit findings for a project, ordered by creation time descending.
     */
    public List<AuditFindingResponse> listFindings(Long proyectoId) {
        return findingRepository.findByProyectoIdOrderByCreatedAtDesc(proyectoId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private AuditFindingResponse toResponse(AuditFinding f) {
        return new AuditFindingResponse(
            f.getId(),
            f.getProyectoId(),
            f.getAuditorId(),
            f.getKybUrl(),
            f.getResultado(),
            f.getObservaciones(),
            f.getRiskScore(),
            f.getFinancialViabilityScore(),
            f.getTxHash(),
            f.getCreatedAt()
        );
    }
}
