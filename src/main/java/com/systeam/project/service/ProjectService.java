package com.systeam.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.project.dto.CreateProjectRequest;
import com.systeam.project.dto.ProjectResponse;
import com.systeam.project.dto.UpdateProjectRequest;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.tokenization.service.TokenizationService;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final TokenizationService tokenizationService;
    private final JdbcTemplate jdbc;

    public ProjectService(ProjectRepository projectRepository,
                          TokenizationService tokenizationService,
                          JdbcTemplate jdbc) {
        this.projectRepository = projectRepository;
        this.tokenizationService = tokenizationService;
        this.jdbc = jdbc;
    }

    public ProjectResponse createProject(CreateProjectRequest request, Long creadorId) {
        String simbolo = request.getSimbolo().toUpperCase();

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE simbolo = ?", Integer.class, simbolo);
        if (count != null && count > 0) {
            throw new ConflictException("El simbolo '" + simbolo + "' ya esta en uso por otro proyecto");
        }

        Usuario creador = new Usuario();
        creador.setId(creadorId);

        Proyecto proyecto = new Proyecto();
        proyecto.setCreador(creador);
        proyecto.setTitulo(request.getTitulo());
        proyecto.setDescripcion(request.getDescripcion());
        proyecto.setMontoRequerido(request.getMontoRequerido());
        proyecto.setPlazo(request.getPlazo());
        proyecto.setEstado("PREPARACION");
        proyecto.setGobernanzaComunidad(request.getGobernanzaComunidad() != null ? request.getGobernanzaComunidad() : false);
        proyecto.setCupoMaximoTokens(request.getCupoMaximoTokens());
        proyecto.setValorNominalToken(request.getValorNominalToken());

        Proyecto saved = projectRepository.save(proyecto);
        jdbc.update("UPDATE projects SET simbolo = ? WHERE id = ?", simbolo, saved.getId());
        return toResponse(saved, simbolo);
    }

    public ProjectResponse updateProject(Long id, UpdateProjectRequest request) {
        Proyecto proyecto = findProjectOrThrow(id);
        String estado = proyecto.getEstado();

        if (List.of("EJECUCION", "FINALIZADO", "CANCELADO", "RECHAZADO").contains(estado)) {
            throw new ConflictException("No se pueden editar proyectos en estado " + estado);
        }

        if ("PREPARACION".equals(estado)) {
            if (request.getTitulo() != null) proyecto.setTitulo(request.getTitulo());
            if (request.getDescripcion() != null) proyecto.setDescripcion(request.getDescripcion());
            if (request.getMontoRequerido() != null) proyecto.setMontoRequerido(request.getMontoRequerido());
            if (request.getPlazo() != null) proyecto.setPlazo(request.getPlazo());
            if (request.getGobernanzaComunidad() != null) proyecto.setGobernanzaComunidad(request.getGobernanzaComunidad());
            if (request.getCupoMaximoTokens() != null) proyecto.setCupoMaximoTokens(request.getCupoMaximoTokens());
            if (request.getValorNominalToken() != null) proyecto.setValorNominalToken(request.getValorNominalToken());

            Proyecto saved = projectRepository.save(proyecto);
            if (request.getSimbolo() != null) {
                jdbc.update("UPDATE projects SET simbolo = ? WHERE id = ?", request.getSimbolo(), id);
            }
            return toResponse(saved, obtenerSimbolo(id));
        }

        if (List.of("EN_AUDITORIA", "FINANCIAMIENTO").contains(estado)) {
            if (request.getTitulo() != null || request.getMontoRequerido() != null || 
                request.getPlazo() != null || request.getCupoMaximoTokens() != null || 
                request.getValorNominalToken() != null || request.getSimbolo() != null) {
                throw new ConflictException("En estado " + estado + " solo se puede editar la descripción. Los demás campos están bloqueados.");
            }
            if (request.getDescripcion() != null) {
                proyecto.setDescripcion(request.getDescripcion());
            }
            Proyecto saved = projectRepository.save(proyecto);
            return toResponse(saved, obtenerSimbolo(id));
        }

        throw new ConflictException("Estado de proyecto desconocido");
    }

    public ProjectResponse getProjectById(Long id) {
        Proyecto proyecto = findProjectOrThrow(id);
        return toResponse(proyecto, obtenerSimbolo(id));
    }

    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    public Page<ProjectResponse> getPublicCatalog(String estado, String search, Pageable pageable) {
        List<String> visibleEstados = List.of("PREPARACION", "FINANCIAMIENTO", "EJECUCION");

        if (estado != null && !estado.isBlank() && !visibleEstados.contains(estado.toUpperCase())) {
            return Page.empty(pageable);
        }

        String estadoFiltro = (estado != null && !estado.isBlank()) ? estado.toUpperCase() : null;
        return projectRepository.findByFilters(estadoFiltro, search, pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    public Page<ProjectResponse> getProjectsByCreator(Long creadorId, Pageable pageable) {
        return projectRepository.findByCreadorId(creadorId, pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    @Transactional
    public void updateProjectStatus(Long projectId, String newEstado) {
        Proyecto proyecto = findProjectOrThrow(projectId);
        String currentEstado = proyecto.getEstado();

        if (!isValidTransition(currentEstado, newEstado)) {
            throw new ConflictException("Transicion de estado invalida: " + currentEstado + " -> " + newEstado);
        }

        if ("FINANCIAMIENTO".equals(currentEstado) && "CANCELADO".equals(newEstado)) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                boolean isAdmin = auth.getAuthorities()
                    .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (!isAdmin) {
                    throw new ConflictException("Solo un administrador puede cancelar un proyecto que ya está en financiamiento");
                }
            }
        }

        proyecto.setEstado(newEstado);

        if ("FINANCIAMIENTO".equals(newEstado)) {
            if (proyecto.getPlazo() == null) {
                proyecto.setPlazo(LocalDateTime.now().plusDays(30));
            }
            crearSubtokenParaProyecto(proyecto);
        }

        projectRepository.save(proyecto);
    }

    private void crearSubtokenParaProyecto(Proyecto proyecto) {
        String simbolo = obtenerSimbolo(proyecto.getId());
        tokenizationService.crearTokenParaProyecto(
            proyecto.getId(),
            proyecto.getTitulo(),
            simbolo,
            proyecto.getCupoMaximoTokens(),
            proyecto.getValorNominalToken(),
            proyecto.getMontoRequerido(),
            proyecto.getPlazo()
        );
    }

    private String obtenerSimbolo(Long projectId) {
        try {
            return jdbc.queryForObject("SELECT simbolo FROM projects WHERE id = ?", String.class, projectId);
        } catch (Exception e) {
            log.warn("No se pudo obtener simbolo para proyecto {}: {}", projectId, e.getMessage());
            return null;
        }
    }

    public ProjectResponse invest(Long projectId, BigDecimal amount) {
        Proyecto proyecto = findProjectOrThrow(projectId);

        if (!"FINANCIAMIENTO".equals(proyecto.getEstado())) {
            throw new ConflictException("Solo se puede invertir en proyectos en estado de financiamiento");
        }

        BigDecimal newAmount = proyecto.getMontoRecaudado().add(amount);
        if (proyecto.getMontoRequerido().compareTo(newAmount) < 0) {
            newAmount = proyecto.getMontoRequerido();
        }
        proyecto.setMontoRecaudado(newAmount);

        return toResponse(projectRepository.save(proyecto), obtenerSimbolo(projectId));
    }

    public void evaluateAndUpdateStates() {
        List<Proyecto> projectsInFinancing = projectRepository.findProjectsInFinancing();

        for (Proyecto proyecto : projectsInFinancing) {
            if (proyecto.getPlazo() != null && LocalDateTime.now().isAfter(proyecto.getPlazo())) {
                boolean fullyFunded = proyecto.getMontoRecaudado() != null
                    && proyecto.getMontoRecaudado().compareTo(proyecto.getMontoRequerido()) >= 0;
                updateProjectStatus(proyecto.getId(), fullyFunded ? "EJECUCION" : "CANCELADO");
            }
        }
    }

    private Proyecto findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + id));
    }

    // Package-visible for AuditService use
    boolean isValidTransition(String from, String to) {
        return switch (from) {
            case "PREPARACION"   -> "EN_AUDITORIA".equals(to) || "CANCELADO".equals(to);
            case "EN_AUDITORIA"  -> "FINANCIAMIENTO".equals(to) || "RECHAZADO".equals(to) || "CANCELADO".equals(to);
            case "FINANCIAMIENTO" -> "EJECUCION".equals(to) || "FINALIZADO".equals(to) || "CANCELADO".equals(to);
            case "EJECUCION"     -> "FINALIZADO".equals(to);
            default              -> false;  // RECHAZADO, CANCELADO, FINALIZADO are terminal
        };
    }

    private ProjectResponse toResponse(Proyecto proyecto, String simbolo) {
        return ProjectResponse.builder()
                .id(proyecto.getId())
                .titulo(proyecto.getTitulo())
                .descripcion(proyecto.getDescripcion())
                .montoRequerido(proyecto.getMontoRequerido())
                .plazo(proyecto.getPlazo())
                .estado(proyecto.getEstado())
                .gobernanzaComunidad(proyecto.getGobernanzaComunidad())
                .cupoMaximoTokens(proyecto.getCupoMaximoTokens())
                .valorNominalToken(proyecto.getValorNominalToken())
                .montoRecaudado(proyecto.getMontoRecaudado())
                .simbolo(simbolo)
                .creadorId(proyecto.getCreador() != null ? proyecto.getCreador().getId() : null)
                .createdAt(proyecto.getCreatedAt())
                .updatedAt(proyecto.getUpdatedAt())
                .esDestacado(proyecto.getEsDestacado())
                .fechaBoost(proyecto.getFechaBoost())
                .montoBoost(proyecto.getMontoBoost())
                .build();
    }
}
