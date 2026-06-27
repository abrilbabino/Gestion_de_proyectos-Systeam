package com.systeam.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.notificaciones.event.ProjectStateChangedEvent;
import com.systeam.project.config.RubroConfig;
import com.systeam.project.dto.CreateProjectRequest;
import com.systeam.project.dto.ProjectResponse;
import com.systeam.project.dto.UpdateProjectRequest;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.user.repository.UserRepository;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.project.repository.HitoRepository;
import com.systeam.shared.model.Hito;
import com.systeam.project.dto.HitoRequest;
import com.systeam.project.dto.HitoResponse;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.tokenization.service.TokenizationService;
import com.systeam.blockchain.service.BlockchainService;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final TokenizationService tokenizationService;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final BlockchainService blockchainService;
    private final HitoRepository hitoRepository;

    public ProjectService(ProjectRepository projectRepository,
                          TokenizationService tokenizationService,
                          JdbcTemplate jdbc,
                          ApplicationEventPublisher eventPublisher,
                          UserRepository userRepository,
                          BlockchainService blockchainService,
                          HitoRepository hitoRepository) {
        this.projectRepository = projectRepository;
        this.tokenizationService = tokenizationService;
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.blockchainService = blockchainService;
        this.hitoRepository = hitoRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, Long creadorId) {
        Usuario creador = userRepository.findById(creadorId)
            .orElseThrow(() -> new ConflictException("Usuario creador no encontrado"));

        if (!"VERIFIED".equals(creador.getKycStatus())) {
            throw new ConflictException("Debes verificar tu identidad antes de poder crear un proyecto.");
        }

        List<String> wallets = jdbc.queryForList("SELECT wallet_address FROM users WHERE id = ?", String.class, creadorId);
        if (wallets.isEmpty() || wallets.get(0) == null || wallets.get(0).trim().isEmpty()) {
            throw new ConflictException("Debes conectar tu billetera antes de poder crear un proyecto.");
        }

        String simbolo = request.getSimbolo().toUpperCase();

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE simbolo = ?", Integer.class, simbolo);
        if (count != null && count > 0) {
            throw new ConflictException("El simbolo '" + simbolo + "' ya esta en uso por otro proyecto");
        }

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
        proyecto.setRubro(request.getRubro() != null ? request.getRubro() : RubroConfig.getRubroIdDefault());

        Proyecto saved = projectRepository.save(proyecto);
        jdbc.update("UPDATE projects SET simbolo = ? WHERE id = ?", simbolo, saved.getId());
        
        if (request.getHitos() != null && !request.getHitos().isEmpty()) {
            if (request.getHitos().size() < 2) {
                throw new ConflictException("Debe haber al menos 2 hitos");
            }
            if (request.getHitos().stream().anyMatch(h -> h.porcentaje().compareTo(new BigDecimal("60.00")) > 0)) {
                throw new ConflictException("Ningún hito puede superar el 60% del total");
            }
            
            LocalDateTime previousDate = proyecto.getPlazo();
            LocalDateTime maxAllowedDate = proyecto.getPlazo().plusMonths(24);
            for (HitoRequest h : request.getHitos()) {
                if (h.plazo().isAfter(maxAllowedDate)) {
                    throw new ConflictException("Ningún hito puede extenderse más allá de 24 meses (2 años) desde el cierre de la campaña");
                }
                if (h.plazo().isBefore(previousDate.plusDays(7))) {
                    throw new ConflictException("Cada hito debe tener un plazo de al menos 7 días respecto al cierre de campaña o al hito anterior en el orden ingresado");
                }
                previousDate = h.plazo();
            }

            BigDecimal totalPorcentaje = request.getHitos().stream()
                .map(HitoRequest::porcentaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalPorcentaje.compareTo(new BigDecimal("100.00")) != 0) {
                throw new ConflictException("La suma de los porcentajes de los hitos debe ser exactamente 100%");
            }
            List<Hito> hitosToSave = request.getHitos().stream().map(h -> 
                Hito.builder()
                    .proyectoId(saved.getId())
                    .titulo(h.titulo())
                    .porcentaje(h.porcentaje())
                    .plazo(h.plazo())
                    .estado(Hito.EstadoHito.PENDIENTE)
                    .build()
            ).toList();
            hitoRepository.saveAll(hitosToSave);
        }

        return toResponse(saved, simbolo);
    }

    @Transactional
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
            if (request.getRubro() != null) proyecto.setRubro(request.getRubro());

            Proyecto saved = projectRepository.save(proyecto);
            if (request.getSimbolo() != null) {
                String nuevoSimbolo = request.getSimbolo().toUpperCase();
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM projects WHERE simbolo = ? AND id != ?", Integer.class, nuevoSimbolo, id);
                if (count != null && count > 0) {
                    throw new ConflictException("El simbolo '" + nuevoSimbolo + "' ya esta en uso por otro proyecto");
                }
                jdbc.update("UPDATE projects SET simbolo = ? WHERE id = ?", nuevoSimbolo, id);
            }

            if (request.getHitos() != null && !request.getHitos().isEmpty()) {
                if (request.getHitos().size() < 2) {
                    throw new ConflictException("Debe haber al menos 2 hitos");
                }
                if (request.getHitos().stream().anyMatch(h -> h.porcentaje().compareTo(new BigDecimal("60.00")) > 0)) {
                    throw new ConflictException("Ningún hito puede superar el 60% del total");
                }

                LocalDateTime previousDate = proyecto.getPlazo();
                LocalDateTime maxAllowedDate = proyecto.getPlazo().plusMonths(24);
                for (HitoRequest h : request.getHitos()) {
                    if (h.plazo().isAfter(maxAllowedDate)) {
                        throw new ConflictException("Ningún hito puede extenderse más allá de 24 meses (2 años) desde el cierre de la campaña");
                    }
                    if (h.plazo().isBefore(previousDate.plusDays(7))) {
                        throw new ConflictException("Cada hito debe tener un plazo de al menos 7 días respecto al cierre de campaña o al hito anterior en el orden ingresado");
                    }
                    previousDate = h.plazo();
                }

                BigDecimal totalPorcentaje = request.getHitos().stream()
                    .map(HitoRequest::porcentaje)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalPorcentaje.compareTo(new BigDecimal("100.00")) != 0) {
                    throw new ConflictException("La suma de los porcentajes de los hitos debe ser exactamente 100%");
                }
                
                // Borrar hitos existentes (como estamos en PREPARACION es seguro)
                List<Hito> existingHitos = hitoRepository.findByProyectoId(id);
                if (!existingHitos.isEmpty()) {
                    hitoRepository.deleteAll(existingHitos);
                }
                
                List<Hito> hitosToSave = request.getHitos().stream().map(h -> 
                    Hito.builder()
                        .proyectoId(id)
                        .titulo(h.titulo())
                        .porcentaje(h.porcentaje())
                        .plazo(h.plazo())
                        .estado(Hito.EstadoHito.PENDIENTE)
                        .createdAt(java.time.LocalDateTime.now())
                        .updatedAt(java.time.LocalDateTime.now())
                        .build()
                ).toList();
                hitoRepository.saveAll(hitosToSave);
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

    @Transactional
    public void publishProject(Long id, String signature, String walletAddress, Long userId) {
        Proyecto proyecto = findProjectOrThrow(id);
        if (!proyecto.getCreador().getId().equals(userId)) {
            throw new ConflictException("Solo el creador puede publicar el proyecto");
        }
        if (!"PREPARACION".equals(proyecto.getEstado())) {
            throw new ConflictException("El proyecto no está en estado PREPARACION");
        }

        String message = "Publicar proyecto: " + proyecto.getTitulo();
        boolean valid = blockchainService.verifySignature(message, signature, walletAddress);
        if (!valid) {
            throw new ConflictException("La firma digital es inválida o no corresponde a la wallet.");
        }

        proyecto.setCreatorSignature(signature);
        proyecto.setEstado("EN_AUDITORIA");
        projectRepository.save(proyecto);

        eventPublisher.publishEvent(new ProjectStateChangedEvent(proyecto.getId(), "PREPARACION", "EN_AUDITORIA", proyecto.getCreador().getId()));
    }

    @Transactional
    public String releaseEscrowFunds(Long id, BigDecimal amount, String escrowAddress) {
        Proyecto proyecto = findProjectOrThrow(id);
        if (!List.of("EJECUCION", "FINALIZADO").contains(proyecto.getEstado())) {
            throw new ConflictException("El proyecto no está en fase de ejecución para liberar fondos.");
        }
        
        try {
            java.math.BigInteger amountWei = amount.multiply(BigDecimal.valueOf(1e18)).toBigInteger();
            return blockchainService.releaseEscrowFunds(escrowAddress, amountWei);
        } catch (Exception e) {
            log.error("Error al liberar fondos del escrow", e);
            throw new ConflictException("Fallo en la blockchain al liberar los fondos del escrow: " + e.getMessage());
        }
    }

    @Transactional
    public String liberarHito(Long projectId, Long hitoId, String comprobanteUrl, String escrowAddress) {
        Proyecto proyecto = findProjectOrThrow(projectId);
        if (!"EJECUCION".equals(proyecto.getEstado())) {
            throw new ConflictException("El proyecto no esta en EJECUCION");
        }
        
        Hito hito = hitoRepository.findById(hitoId)
            .orElseThrow(() -> new ResourceNotFoundException("Hito no encontrado"));
            
        if (!hito.getProyectoId().equals(projectId)) {
            throw new ConflictException("El hito no pertenece a este proyecto");
        }
        
        if (hito.getEstado() == Hito.EstadoHito.COMPLETADO) {
            throw new ConflictException("El hito ya fue liberado");
        }
        
        BigDecimal montoRecaudado = proyecto.getMontoRecaudado();
        if (montoRecaudado == null || montoRecaudado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("El proyecto no tiene fondos recaudados");
        }
        
        BigDecimal amountToRelease = montoRecaudado.multiply(hito.getPorcentaje()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        
        String txHash = releaseEscrowFunds(projectId, amountToRelease, escrowAddress);
        
        hito.setEstado(Hito.EstadoHito.COMPLETADO);
        hito.setComprobanteUrl(comprobanteUrl);
        hitoRepository.save(hito);
        
        return txHash;
    }

    public ProjectResponse getProjectById(Long id) {
        Proyecto proyecto = findProjectOrThrow(id);
        return toResponse(proyecto, obtenerSimbolo(id));
    }

    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    public Page<ProjectResponse> getPublicCatalog(String estado, String search, Pageable pageable) {
        List<String> visibleEstados = List.of("PREPARACION", "FINANCIAMIENTO", "EJECUCION", "AUDITADO");

        if (estado != null && !estado.isBlank() && !visibleEstados.contains(estado.toUpperCase())) {
            return Page.empty(pageable);
        }

        List<String> estadosFiltro = (estado != null && !estado.isBlank()) 
            ? List.of(estado.toUpperCase()) 
            : visibleEstados;
            
        return projectRepository.findByFilters(estadosFiltro, search, pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    public Page<ProjectResponse> getProjectsByCreator(Long creadorId, Pageable pageable) {
        return projectRepository.findByCreadorId(creadorId, pageable).map(p -> toResponse(p, obtenerSimbolo(p.getId())));
    }

    public Page<ProjectResponse> getPublicProjectsByCreator(Long creadorId, Pageable pageable) {
        List<String> visibleEstados = List.of("PREPARACION", "FINANCIAMIENTO", "EJECUCION", "AUDITADO", "FINALIZADO");
        return projectRepository.findByCreadorIdAndFilters(creadorId, visibleEstados, pageable)
                .map(p -> toResponse(p, obtenerSimbolo(p.getId())));
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
            if (proyecto.getValorNominalToken() != null && proyecto.getCupoMaximoTokens() != null) {
                BigDecimal recaudacionMaxima = proyecto.getValorNominalToken()
                    .multiply(BigDecimal.valueOf(proyecto.getCupoMaximoTokens()));
                  if (recaudacionMaxima.compareTo(proyecto.getMontoRequerido()) != 0) {
                      throw new ConflictException("No se puede publicar el proyecto: el valor total de los subtokens (" + recaudacionMaxima + ") debe ser exactamente igual a la meta de financiamiento (" + proyecto.getMontoRequerido() + ")");
                  }
            }

            if (proyecto.getPlazo() == null) {
                proyecto.setPlazo(LocalDateTime.now().plusDays(30));
            }
            crearSubtokenParaProyecto(proyecto);
        }

        projectRepository.save(proyecto);

        eventPublisher.publishEvent(new ProjectStateChangedEvent(projectId, currentEstado, newEstado, null));
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

    private long[] obtenerVotos(Long projectId) {
        try {
            return jdbc.queryForObject(
                "SELECT for_votes, against_votes, total_votes FROM projects WHERE id = ?",
                (rs, rowNum) -> new long[]{
                    rs.getBigDecimal("for_votes").longValue(),
                    rs.getBigDecimal("against_votes").longValue(),
                    rs.getBigDecimal("total_votes").longValue()
                }, projectId);
        } catch (Exception e) {
            return new long[]{0, 0, 0};
        }
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
            case "EN_AUDITORIA"  -> "AUDITADO".equals(to) || "RECHAZADO".equals(to) || "CANCELADO".equals(to);
            case "AUDITADO"      -> "FINANCIAMIENTO".equals(to) || "CANCELADO".equals(to);
            case "FINANCIAMIENTO" -> "EJECUCION".equals(to) || "FINALIZADO".equals(to) || "CANCELADO".equals(to);
            case "EJECUCION"     -> "FINALIZADO".equals(to);
            default              -> false;  // RECHAZADO, CANCELADO, FINALIZADO are terminal
        };
    }

    private ProjectResponse toResponse(Proyecto proyecto, String simbolo) {
        long[] votos = obtenerVotos(proyecto.getId());
        
        List<Hito> hitos = hitoRepository.findByProyectoId(proyecto.getId());
        List<HitoResponse> hitosResponse = hitos.stream()
            .map(h -> new HitoResponse(h.getId(), h.getTitulo(), h.getPorcentaje(), h.getPlazo(), h.getEstado().name(), h.getComprobanteUrl()))
            .toList();

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
                .rubro(proyecto.getRubro())
                .forVotes(votos[0])
                .againstVotes(votos[1])
                .totalVotes(votos[2])
                .hitos(hitosResponse)
                .build();
    }
}
