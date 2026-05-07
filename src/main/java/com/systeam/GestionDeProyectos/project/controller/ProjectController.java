package com.systeam.GestionDeProyectos.project.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@RequestBody @Valid CreateProjectRequest request, Principal principal) {
        Long creadorId = getCurrentUserId(principal);
        return projectService.createProject(request, creadorId);
    }

    @PutMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable Long id, @RequestBody @Valid UpdateProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    @GetMapping("/{id}")
    public ProjectResponse getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id);
    }

    @GetMapping
    public Page<ProjectResponse> getAllProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getAllProjects(PageRequest.of(page, size));
    }

    @GetMapping("/catalog")
    public Page<ProjectResponse> getPublicCatalog(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getPublicCatalog(estado, search, PageRequest.of(page, size));
    }

    @GetMapping("/my-projects")
    public Page<ProjectResponse> getMyProjects(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long creadorId = getCurrentUserId(principal);
        return projectService.getProjectsByCreator(creadorId, PageRequest.of(page, size));
    }

    @PatchMapping("/{id}/status")
    public ProjectResponse updateProjectStatus(@PathVariable Long id, @RequestParam String estado) {
        projectService.updateProjectStatus(id, estado.toUpperCase());
        return projectService.getProjectById(id);
    }

    @PostMapping("/evaluate-states")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evaluateAndUpdateStates() {
        projectService.evaluateAndUpdateStates();
    }

    private Long getCurrentUserId(Principal principal) {
        return 1L;
    }
}
