package com.systeam.GestionDeProyectos.project.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.systeam.GestionDeProyectos.project.dto.CreateProjectRequest;
import com.systeam.GestionDeProyectos.project.dto.ProjectResponse;
import com.systeam.GestionDeProyectos.project.dto.UpdateProjectRequest;
import com.systeam.GestionDeProyectos.project.exception.ConflictException;
import com.systeam.GestionDeProyectos.project.exception.ResourceNotFoundException;
import com.systeam.GestionDeProyectos.project.repository.ProjectRepository;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse createProject(CreateProjectRequest request, Long creadorId) {
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

        return toResponse(projectRepository.save(proyecto));
    }

    public ProjectResponse updateProject(Long id, UpdateProjectRequest request) {
        Proyecto proyecto = findProjectOrThrow(id);

        if (!"PREPARACION".equals(proyecto.getEstado())) {
            throw new ConflictException("Solo se pueden editar proyectos en estado de preparacion");
        }

        if (request.getTitulo() != null) proyecto.setTitulo(request.getTitulo());
        if (request.getDescripcion() != null) proyecto.setDescripcion(request.getDescripcion());
        if (request.getMontoRequerido() != null) proyecto.setMontoRequerido(request.getMontoRequerido());
        if (request.getPlazo() != null) proyecto.setPlazo(request.getPlazo());
        if (request.getGobernanzaComunidad() != null) proyecto.setGobernanzaComunidad(request.getGobernanzaComunidad());
        if (request.getCupoMaximoTokens() != null) proyecto.setCupoMaximoTokens(request.getCupoMaximoTokens());
        if (request.getValorNominalToken() != null) proyecto.setValorNominalToken(request.getValorNominalToken());

        return toResponse(projectRepository.save(proyecto));
    }

    public ProjectResponse getProjectById(Long id) {
        return toResponse(findProjectOrThrow(id));
    }

    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<ProjectResponse> getPublicCatalog(String estado, String search, Pageable pageable) {
        List<String> visibleEstados = List.of("PREPARACION", "FINANCIAMIENTO", "EJECUCION");

        if (estado != null && !estado.isBlank() && !visibleEstados.contains(estado.toUpperCase())) {
            return Page.empty(pageable);
        }

        String estadoFiltro = (estado != null && !estado.isBlank()) ? estado.toUpperCase() : null;
        return projectRepository.findByFilters(estadoFiltro, search, pageable).map(this::toResponse);
    }

    public Page<ProjectResponse> getProjectsByCreator(Long creadorId, Pageable pageable) {
        return projectRepository.findByCreadorId(creadorId, pageable).map(this::toResponse);
    }

    public void updateProjectStatus(Long projectId, String newEstado) {
        Proyecto proyecto = findProjectOrThrow(projectId);
        String currentEstado = proyecto.getEstado();

        if (!isValidTransition(currentEstado, newEstado)) {
            throw new ConflictException("Transicion de estado invalida: " + currentEstado + " -> " + newEstado);
        }

        proyecto.setEstado(newEstado);

        if ("FINANCIAMIENTO".equals(newEstado) && proyecto.getPlazo() == null) {
            proyecto.setPlazo(LocalDateTime.now().plusDays(30));
        }

        projectRepository.save(proyecto);
    }

    public void evaluateAndUpdateStates() {
        List<Proyecto> projectsInFinancing = projectRepository.findProjectsInFinancing();

        for (Proyecto proyecto : projectsInFinancing) {
            if (proyecto.getPlazo() != null && LocalDateTime.now().isAfter(proyecto.getPlazo())) {
                updateProjectStatus(proyecto.getId(), "FINALIZADO");
            }
        }
    }

    private Proyecto findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + id));
    }

    private boolean isValidTransition(String from, String to) {
        return switch (from) {
            case "PREPARACION" -> "FINANCIAMIENTO".equals(to);
            case "FINANCIAMIENTO" -> "EJECUCION".equals(to) || "FINALIZADO".equals(to);
            case "EJECUCION" -> "FINALIZADO".equals(to);
            default -> false;
        };
    }

    private ProjectResponse toResponse(Proyecto proyecto) {
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
                .creadorId(proyecto.getCreador() != null ? proyecto.getCreador().getId() : null)
                .createdAt(proyecto.getCreatedAt())
                .updatedAt(proyecto.getUpdatedAt())
                .build();
    }
}
