package com.systeam.GestionDeProyectos.project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.systeam.GestionDeProyectos.project.dto.CreateProjectRequest;
import com.systeam.GestionDeProyectos.project.dto.ProjectResponse;
import com.systeam.GestionDeProyectos.project.dto.UpdateProjectRequest;
import com.systeam.GestionDeProyectos.project.exception.ConflictException;
import com.systeam.GestionDeProyectos.project.exception.ResourceNotFoundException;
import com.systeam.GestionDeProyectos.project.model.Project;
import com.systeam.GestionDeProyectos.project.model.ProjectStatus;
import com.systeam.GestionDeProyectos.project.repository.ProjectRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final SmartContractService smartContractService;

    public ProjectService(ProjectRepository projectRepository, SmartContractService smartContractService) {
        this.projectRepository = projectRepository;
        this.smartContractService = smartContractService;
    }

    public ProjectResponse createProject(CreateProjectRequest request, Long creatorId) {
        if (request.getCantidadDeTokens() != null && request.getValorNominal() != null) {
            validateTokenMath(request.getRequiredAmount(), request.getCantidadDeTokens(), request.getValorNominal());
        }

        Project project = Project.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .objective(request.getObjective())
                .requiredAmount(request.getRequiredAmount())
                .currentAmount(BigDecimal.ZERO)
                .status(ProjectStatus.PREPARACION)
                .creatorId(creatorId)
                .cantidadDeTokens(request.getCantidadDeTokens())
                .valorNominal(request.getValorNominal())
                .build();

        return toResponse(projectRepository.save(project));
    }

    public ProjectResponse updateProject(Long id, UpdateProjectRequest request) {
        Project project = findProjectOrThrow(id);

        if (project.getStatus() != ProjectStatus.PREPARACION) {
            throw new ConflictException("Solo se pueden editar proyectos en estado de preparacion");
        }

        if (request.getTitle() != null) {
            project.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getObjective() != null) {
            project.setObjective(request.getObjective());
        }
        if (request.getRequiredAmount() != null) {
            project.setRequiredAmount(request.getRequiredAmount());
        }
        if (request.getCantidadDeTokens() != null) {
            project.setCantidadDeTokens(request.getCantidadDeTokens());
        }
        if (request.getValorNominal() != null) {
            project.setValorNominal(request.getValorNominal());
        }

        if (project.getCantidadDeTokens() != null && project.getValorNominal() != null) {
            validateTokenMath(project.getRequiredAmount(), project.getCantidadDeTokens(), project.getValorNominal());
        }

        return toResponse(projectRepository.save(project));
    }

    public ProjectResponse getProjectById(Long id) {
        Project project = findProjectOrThrow(id);
        return toResponse(project);
    }

    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<ProjectResponse> getPublicCatalog(String status, String search, Pageable pageable) {
        ProjectStatus projectStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                projectStatus = ProjectStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Estado de proyecto invalido: " + status);
            }
        }

        List<ProjectStatus> visibleStatus = List.of(
            ProjectStatus.FINANCIAMIENTO,
            ProjectStatus.EJECUCION,
            ProjectStatus.PREPARACION
        );

        if (projectStatus != null && !visibleStatus.contains(projectStatus)) {
            return Page.empty(pageable);
        }

        return projectRepository.findByFilters(projectStatus, search, pageable).map(this::toResponse);
    }

    public Page<ProjectResponse> getProjectsByCreator(Long creatorId, Pageable pageable) {
        return projectRepository.findByCreatorId(creatorId, pageable).map(this::toResponse);
    }

    public void updateProjectStatus(Long projectId, ProjectStatus newStatus) {
        Project project = findProjectOrThrow(projectId);
        ProjectStatus currentStatus = project.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new ConflictException("Transicion de estado invalida: " + currentStatus + " -> " + newStatus);
        }

        project.setStatus(newStatus);

        if (newStatus == ProjectStatus.FINANCIAMIENTO && project.getFinancingStartDate() == null) {
            project.setFinancingStartDate(LocalDateTime.now());
            project.setFinancingEndDate(LocalDateTime.now().plusDays(30));
        }

        if (newStatus == ProjectStatus.EJECUCION) {
            project.setFinancingEndDate(LocalDateTime.now());
        }

        projectRepository.save(project);
    }

    public void evaluateAndUpdateStates() {
        List<Project> projectsInFinancing = projectRepository.findProjectsInFinancing();

        for (Project project : projectsInFinancing) {
            if (project.getCurrentAmount().compareTo(project.getRequiredAmount()) >= 0) {
                updateProjectStatus(project.getId(), ProjectStatus.EJECUCION);
            } else if (project.getFinancingEndDate() != null &&
                       LocalDateTime.now().isAfter(project.getFinancingEndDate())) {
                updateProjectStatus(project.getId(), ProjectStatus.FINALIZADO);
            }
        }
    }

    public ProjectResponse addInvestment(Long projectId, BigDecimal amount) {
        Project project = findProjectOrThrow(projectId);

        if (project.getStatus() != ProjectStatus.FINANCIAMIENTO) {
            throw new ConflictException("El proyecto no esta en estado de financiamiento");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto de inversion debe ser mayor a cero");
        }

        project.setCurrentAmount(project.getCurrentAmount().add(amount));

        if (project.getCurrentAmount().compareTo(project.getRequiredAmount()) >= 0) {
            project.setCurrentAmount(project.getRequiredAmount());
            project.setStatus(ProjectStatus.EJECUCION);
            project.setFinancingEndDate(LocalDateTime.now());
        }

        return toResponse(projectRepository.save(project));
    }

    public Map<String, Object> getFinancingProgress(Long projectId) {
        Project project = findProjectOrThrow(projectId);

        BigDecimal progress = calculateProgress(project);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("projectId", project.getId());
        result.put("requiredAmount", project.getRequiredAmount());
        result.put("currentAmount", project.getCurrentAmount());
        result.put("progressPercentage", progress);
        result.put("status", project.getStatus().toString());
        result.put("isComplete", progress.compareTo(new BigDecimal("100")) >= 0);

        return result;
    }

    public Map<String, Object> getSmartContractInfo(Long projectId) {
        Project project = findProjectOrThrow(projectId);

        if (project.getSmartContractAddress() == null) {
            throw new ResourceNotFoundException("El proyecto no tiene un smart contract asociado");
        }

        return smartContractService.getContractInfo(project.getSmartContractAddress());
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + id));
    }

    private void validateTokenMath(BigDecimal requiredAmount, Long cantidadDeTokens, BigDecimal valorNominal) {
        if (cantidadDeTokens <= 0) {
            throw new RuntimeException("La cantidad de tokens debe ser mayor a cero");
        }
        if (valorNominal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El valor nominal debe ser mayor a cero");
        }

        BigDecimal calculatedAmount = valorNominal.multiply(BigDecimal.valueOf(cantidadDeTokens));

        if (calculatedAmount.compareTo(requiredAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new ConflictException(
                "La cantidad de tokens multiplicada por su valor nominal debe coincidir con el monto requerido. " +
                "Calculado: " + calculatedAmount + ", Requerido: " + requiredAmount
            );
        }
    }

    private boolean isValidTransition(ProjectStatus from, ProjectStatus to) {
        return switch (from) {
            case PREPARACION -> to == ProjectStatus.FINANCIAMIENTO;
            case FINANCIAMIENTO -> to == ProjectStatus.EJECUCION || to == ProjectStatus.FINALIZADO;
            case EJECUCION -> to == ProjectStatus.FINALIZADO;
            case FINALIZADO -> false;
        };
    }

    private BigDecimal calculateProgress(Project project) {
        if (project.getRequiredAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return project.getCurrentAmount()
                .multiply(new BigDecimal("100"))
                .divide(project.getRequiredAmount(), 2, RoundingMode.HALF_UP);
    }

    private ProjectResponse toResponse(Project project) {
        BigDecimal progress = calculateProgress(project);

        ProjectResponse.SmartContractInfo contractInfo = null;
        if (project.getSmartContractAddress() != null) {
            Map<String, Object> info = smartContractService.getContractInfo(project.getSmartContractAddress());
            contractInfo = ProjectResponse.SmartContractInfo.builder()
                    .address(project.getSmartContractAddress())
                    .totalSubTokens((Long) info.getOrDefault("totalSubTokens", 0L))
                    .distribution((String) info.getOrDefault("distribution", "N/A"))
                    .build();
        }

        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .objective(project.getObjective())
                .requiredAmount(project.getRequiredAmount())
                .currentAmount(project.getCurrentAmount())
                .status(project.getStatus())
                .creatorId(project.getCreatorId())
                .cantidadDeTokens(project.getCantidadDeTokens())
                .valorNominal(project.getValorNominal())
                .smartContractAddress(project.getSmartContractAddress())
                .financingStartDate(project.getFinancingStartDate())
                .financingEndDate(project.getFinancingEndDate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .financingProgress(progress)
                .smartContractInfo(contractInfo)
                .build();
    }
}
