package com.systeam.project.controller;

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

import com.systeam.project.dto.BoostProjectRequest;
import com.systeam.project.dto.CreateProjectRequest;
import com.systeam.project.dto.ProjectResponse;
import com.systeam.project.dto.ProjectVoteRequest;
import com.systeam.project.dto.UpdateProjectRequest;
import com.systeam.project.service.BoostService;
import com.systeam.project.service.ProjectService;
import com.systeam.project.service.ProjectVoteService;
import com.systeam.project.service.ProjectVoteStreamRegistry;
import com.systeam.security.JwtPrincipal;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final BoostService boostService;
    private final ProjectVoteService projectVoteService;
    private final ProjectVoteStreamRegistry projectVoteStreamRegistry;

    public ProjectController(ProjectService projectService, BoostService boostService,
                             ProjectVoteService projectVoteService,
                             ProjectVoteStreamRegistry projectVoteStreamRegistry) {
        this.projectService = projectService;
        this.boostService = boostService;
        this.projectVoteService = projectVoteService;
        this.projectVoteStreamRegistry = projectVoteStreamRegistry;
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

    @GetMapping("/creator/{creatorId}")
    public Page<ProjectResponse> getPublicProjectsByCreator(
            @PathVariable Long creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getPublicProjectsByCreator(creatorId, PageRequest.of(page, size));
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

    @PostMapping("/{id}/boost")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('investment:create') or hasAuthority('project:update')")
    public void boostProject(
            @PathVariable Long id,
            @RequestBody @Valid BoostProjectRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        boostService.boostProject(id, principal.userId(), request.getTxHash());
    }

    @PostMapping("/{id}/vote")
    @PreAuthorize("hasAuthority('governance:vote')")
    public String voteProject(@PathVariable Long id,
                              @RequestBody @Valid ProjectVoteRequest request,
                              @AuthenticationPrincipal JwtPrincipal user) {
        projectVoteService.validateVote(user.userId(), id);
        try {
            return projectVoteService.vote(user.userId(), id, request.getSupport());
        } catch (Exception e) {
            throw new RuntimeException("Error al votar proyecto: " + e.getMessage(), e);
        }
    }

    @GetMapping(value = "/{id}/votes/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter projectVoteStream(@PathVariable Long id) {
        return projectVoteStreamRegistry.subscribe(id);
    }

    @PostMapping("/evaluate-states")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void evaluateAndUpdateStates() {
        projectService.evaluateAndUpdateStates();
    }
}
