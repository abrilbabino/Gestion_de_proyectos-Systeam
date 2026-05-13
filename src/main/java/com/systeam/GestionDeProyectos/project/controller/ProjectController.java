package com.systeam.GestionDeProyectos.project.controller;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.GestionDeProyectos.project.dto.CreateProjectRequest;
import com.systeam.GestionDeProyectos.project.dto.ProjectResponse;
import com.systeam.GestionDeProyectos.project.dto.UpdateProjectRequest;
import com.systeam.GestionDeProyectos.project.service.ProjectService;
import com.systeam.GestionDeProyectos.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // Solo CREATOR y ADMIN tienen el permiso project:create
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:create')")
    public ProjectResponse createProject(
            @RequestBody @Valid CreateProjectRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return projectService.createProject(request, principal.userId());
    }

    // Solo CREATOR y ADMIN tienen project:update
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ProjectResponse updateProject(@PathVariable Long id, @RequestBody @Valid UpdateProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    // CREATOR, INVESTOR y ADMIN tienen project:read
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:read')")
    public ProjectResponse getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id);
    }

    // CREATOR, INVESTOR y ADMIN tienen project:read
    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public Page<ProjectResponse> getAllProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getAllProjects(PageRequest.of(page, size));
    }

    // Catálogo público: permitido sin token (configurado en SecurityConfig)
    @GetMapping("/catalog")
    public Page<ProjectResponse> getPublicCatalog(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getPublicCatalog(estado, search, PageRequest.of(page, size));
    }

    // Cualquier usuario autenticado puede ver sus propios proyectos
    @GetMapping("/my-projects")
    public Page<ProjectResponse> getMyProjects(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getProjectsByCreator(principal.userId(), PageRequest.of(page, size));
    }

    // Cambiar estado requiere project:update (CREATOR/ADMIN) o ser ADMIN
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:update')")
    public ProjectResponse updateProjectStatus(@PathVariable Long id, @RequestParam String status) {
        projectService.updateProjectStatus(id, status.toUpperCase());
        return projectService.getProjectById(id);
    }

    // Evaluar estados automáticamente: solo ADMIN
    @PostMapping("/{id}/invest")
    @PreAuthorize("hasAuthority('investment:create')")
    public ProjectResponse invest(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return projectService.invest(id, amount);
    }

    @PostMapping("/evaluate-states")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void evaluateAndUpdateStates() {
        projectService.evaluateAndUpdateStates();
    }
}
