package com.systeam.GestionDeProyectos.project.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.systeam.GestionDeProyectos.project.model.ProjectStatus;
import com.systeam.GestionDeProyectos.project.service.ProjectService;
import com.systeam.GestionDeProyectos.project.service.SmartContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SmartContractService smartContractService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@RequestBody @Valid CreateProjectRequest request, Principal principal) {
        Long creatorId = getCurrentUserId(principal);
        return projectService.createProject(request, creatorId);
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.getPublicCatalog(status, search, PageRequest.of(page, size));
    }

    @GetMapping("/my-projects")
    public Page<ProjectResponse> getMyProjects(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long creatorId = getCurrentUserId(principal);
        return projectService.getProjectsByCreator(creatorId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}/financing-progress")
    public Map<String, Object> getFinancingProgress(@PathVariable Long id) {
        return projectService.getFinancingProgress(id);
    }

    @GetMapping("/{id}/smart-contract")
    public Map<String, Object> getSmartContractInfo(@PathVariable Long id) {
        return projectService.getSmartContractInfo(id);
    }

    @PostMapping("/{id}/invest")
    public ProjectResponse investInProject(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return projectService.addInvestment(id, amount);
    }

    @PatchMapping("/{id}/status")
    public ProjectResponse updateProjectStatus(@PathVariable Long id, @RequestParam String status) {
        ProjectStatus projectStatus = ProjectStatus.valueOf(status.toUpperCase());
        projectService.updateProjectStatus(id, projectStatus);
        return projectService.getProjectById(id);
    }

    @PostMapping("/evaluate-states")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evaluateAndUpdateStates() {
        projectService.evaluateAndUpdateStates();
    }

    private Long getCurrentUserId(Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Usuario no autenticado");
        }
        return 1L;
    }
}
