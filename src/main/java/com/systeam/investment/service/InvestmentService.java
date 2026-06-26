package com.systeam.investment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.investment.dto.CreateInvestmentRequest;
import com.systeam.investment.dto.InvestmentResponse;
import com.systeam.investment.dto.ValidateInvestmentRequest;
import com.systeam.investment.dto.ValidateInvestmentResponse;
import com.systeam.investment.repository.InvestmentRepository;
import com.systeam.notificaciones.event.InvestmentConfirmedEvent;
import com.systeam.notificaciones.event.ProjectStateChangedEvent;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.investment.service.SmartContractService;
import com.systeam.shared.model.Inversion;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.tokenization.service.DynamicPricingService;
import com.systeam.tokenization.service.SubtokenService;
import com.systeam.blockchain.service.IdeafyFactoryService;
import com.systeam.blockchain.service.OfferingContractService;
import java.math.BigInteger;

import org.springframework.context.ApplicationEventPublisher;

@Service
public class InvestmentService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);

    private final InvestmentRepository investmentRepository;
    private final SmartContractService smartContractService;
    private final JdbcTemplate jdbc;
    private final SubtokenService subtokenService;
    private final DynamicPricingService dynamicPricingService;
    private final IdeafyFactoryService ideafyFactoryService;
    private final OfferingContractService offeringContractService;
    private final ApplicationEventPublisher eventPublisher;

    public InvestmentService(InvestmentRepository investmentRepository,
                             SmartContractService smartContractService,
                             JdbcTemplate jdbc,
                             SubtokenService subtokenService,
                             DynamicPricingService dynamicPricingService,
                             IdeafyFactoryService ideafyFactoryService,
                             OfferingContractService offeringContractService,
                             ApplicationEventPublisher eventPublisher) {
        this.investmentRepository = investmentRepository;
        this.smartContractService = smartContractService;
        this.jdbc = jdbc;
        this.subtokenService = subtokenService;
        this.dynamicPricingService = dynamicPricingService;
        this.ideafyFactoryService = ideafyFactoryService;
        this.offeringContractService = offeringContractService;
        this.eventPublisher = eventPublisher;
    }

    public ValidateInvestmentResponse validateInvestment(ValidateInvestmentRequest request, Long usuarioId) {
        Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
        String estado = (String) proyecto.get("estado");

        if (!"FINANCIAMIENTO".equals(estado)) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El proyecto no esta en estado de financiamiento")
                    .build();
        }

        Map<String, Object> subtoken = subtokenService.findSubtokenByProject(request.getProyectoId());
        if (subtoken == null) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El proyecto no tiene un subtoken asociado")
                    .build();
        }

        int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
        BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");

        BigDecimal montoRequerido = (BigDecimal) proyecto.get("montoRequerido");
        BigDecimal montoRecaudado = obtenerMontoRecaudadoOnChain(request.getProyectoId());
        BigDecimal precioSubtoken = dynamicPricingService.calcularPrecioFinanciamiento(
            precioBase, montoRecaudado, montoRequerido
        );

        BigDecimal descuentoPorcentaje = getCrossRewardDiscountPercentage(usuarioId, request.getProyectoId());
        if (descuentoPorcentaje.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.valueOf(100).subtract(descuentoPorcentaje).divide(BigDecimal.valueOf(100));
            precioSubtoken = precioSubtoken.multiply(multiplier);
        }

        if (cupoRestante <= 0) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El proyecto no tiene cupo disponible")
                    .cupoDisponible(0)
                    .precioSubtoken(precioSubtoken)
                    .subTokensARecebir(0)
                    .build();
        }

        int subTokensNecesarios = request.getMontoIdea()
                .divide(precioSubtoken, 0, RoundingMode.DOWN)
                .intValue();

        if (subTokensNecesarios <= 0) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El monto es insuficiente para recibir al menos 1 sub-token")
                    .cupoDisponible(cupoRestante)
                    .precioSubtoken(precioSubtoken)
                    .subTokensARecebir(0)
                    .descuentoPorcentaje(descuentoPorcentaje)
                    .build();
        }

        try {
            BigInteger treasuryBalance = ideafyFactoryService.treasuryBalanceOfProject(request.getProyectoId());
            BigInteger supply18 = BigInteger.valueOf(subTokensNecesarios).multiply(BigInteger.TEN.pow(18));
            if (treasuryBalance.compareTo(supply18) < 0) {
                return ValidateInvestmentResponse.builder()
                        .valido(false)
                        .mensaje("La tesoreria no dispone de suficientes sub-tokens on-chain para esta inversion")
                        .cupoDisponible(cupoRestante)
                        .precioSubtoken(precioSubtoken)
                        .subTokensARecebir(0)
                        .build();
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar balance on-chain del treasury: {}", e.getMessage());
        }

        if (subTokensNecesarios > cupoRestante) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("La cantidad solicitada supera el cupo disponible del proyecto")
                    .cupoDisponible(cupoRestante)
                    .precioSubtoken(precioSubtoken)
                    .subTokensARecebir(subTokensNecesarios)
                    .descuentoPorcentaje(descuentoPorcentaje)
                    .build();
        }

        try {
            subtokenService.validateMaxOwnership(usuarioId, ((Number) subtoken.get("id")).longValue(), subTokensNecesarios);
        } catch (ConflictException e) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje(e.getMessage())
                    .cupoDisponible(cupoRestante)
                    .precioSubtoken(precioSubtoken)
                    .subTokensARecebir(subTokensNecesarios)
                    .descuentoPorcentaje(descuentoPorcentaje)
                    .build();
        }

        return ValidateInvestmentResponse.builder()
                .valido(true)
                .mensaje("Inversion valida")
                .cupoDisponible(cupoRestante)
                .precioSubtoken(precioSubtoken)
                .subTokensARecebir(subTokensNecesarios)
                .descuentoPorcentaje(descuentoPorcentaje)
                .build();
    }

    @Transactional
    public InvestmentResponse createInvestment(CreateInvestmentRequest request, Long usuarioId) {
        Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
        String estado = (String) proyecto.get("estado");

        if (!"FINANCIAMIENTO".equals(estado)) {
            throw new ConflictException("El proyecto no esta en estado de financiamiento");
        }

        Map<String, Object> subtoken = subtokenService.findSubtokenByProject(request.getProyectoId());
        if (subtoken == null) {
            throw new ConflictException("El proyecto no tiene un subtoken asociado para invertir");
        }

        Long subtokenId = ((Number) subtoken.get("id")).longValue();
        int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
        BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
        int suministroTotal = (int) subtoken.get("suministro_total");
        BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

        BigDecimal montoRequerido = (BigDecimal) proyecto.get("montoRequerido");
        BigDecimal montoRecaudado = obtenerMontoRecaudadoOnChain(request.getProyectoId());
        BigDecimal precioSubtoken = dynamicPricingService.calcularPrecioFinanciamiento(
            precioBase, montoRecaudado, montoRequerido
        );
        BigDecimal precioSinDescuento = precioSubtoken;

        BigDecimal descuentoPorcentaje = getCrossRewardDiscountPercentage(usuarioId, request.getProyectoId());
        if (descuentoPorcentaje.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.valueOf(100).subtract(descuentoPorcentaje).divide(BigDecimal.valueOf(100));
            precioSubtoken = precioSubtoken.multiply(multiplier);
        }

        int subTokens = request.getMontoIdea()
                .divide(precioSubtoken, 0, RoundingMode.DOWN)
                .intValue();

        if (subTokens <= 0) {
            throw new ConflictException("El monto es insuficiente para recibir al menos 1 sub-token");
        }

        try {
            BigInteger treasuryBalance = ideafyFactoryService.treasuryBalanceOfProject(request.getProyectoId());
            BigInteger supply18 = BigInteger.valueOf(subTokens).multiply(BigInteger.TEN.pow(18));
            if (treasuryBalance.compareTo(supply18) < 0) {
                throw new ConflictException("La tesoreria no dispone de suficientes sub-tokens on-chain para esta inversion");
            }
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo verificar balance on-chain del treasury: {}", e.getMessage());
        }

        if (subTokens > cupoRestante) {
            throw new ConflictException("La cantidad de tokens solicitada supera el cupo disponible del proyecto");
        }

        subtokenService.validateMaxOwnership(usuarioId, subtokenId, subTokens);

        String txHash = request.getTxHash();

        Integer txHashCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM investments WHERE tx_hash = ?", Integer.class, txHash
        );
        if (txHashCount != null && txHashCount > 0) {
            throw new ConflictException("La transaccion blockchain ya fue registrada");
        }

        if (!smartContractService.verifyTransaction(txHash)) {
            throw new ConflictException("La transaccion blockchain no fue encontrada o fallo en Sepolia");
        }

        BigDecimal montoAporteReal = precioSinDescuento.multiply(BigDecimal.valueOf(subTokens));

        jdbc.update("UPDATE projects SET monto_recaudado = COALESCE(monto_recaudado, 0) + ? WHERE id = ?",
            montoAporteReal, request.getProyectoId());

        BigDecimal totalOnChain = obtenerMontoRecaudadoOnChain(request.getProyectoId());
        if (totalOnChain.compareTo(montoRequerido) >= 0) {
            jdbc.update("UPDATE projects SET estado = 'EJECUCION', updated_at = NOW() WHERE id = ?",
                request.getProyectoId());
            eventPublisher.publishEvent(new ProjectStateChangedEvent(
                request.getProyectoId(), "FINANCIAMIENTO", "EJECUCION", null));
            log.info("Project {} reached funding goal. Transitioned to EJECUCION.", request.getProyectoId());
        }

        BigDecimal nuevoPrecio = dynamicPricingService.calcularPrecioFinanciamiento(
            precioBase, totalOnChain, montoRequerido
        );
        subtokenService.updateQuotaAndPrice(subtokenId, subTokens, nuevoPrecio);

        subtokenService.addPortfolioEntry(usuarioId, subtokenId, subTokens);

        // Alocación on-chain (10^18)
        String walletAddress = request.getWalletAddress();
        if (walletAddress != null && !walletAddress.isEmpty()) {
            try {
                BigInteger amountOnChain = BigInteger.valueOf(subTokens).multiply(BigInteger.TEN.pow(18));
                String allocateTx = ideafyFactoryService.allocateTokens(request.getProyectoId(), walletAddress, amountOnChain);
                log.info("Tokens on-chain allocated for project {}: {}", request.getProyectoId(), allocateTx);
            } catch (Exception e) {
                log.error("Failed to allocate tokens on-chain for project {}: {}", request.getProyectoId(), e.getMessage());
                throw new RuntimeException("Error al asignar tokens en la blockchain: " + e.getMessage());
            }
        }

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);

        Proyecto proyectoEntity = new Proyecto();
        proyectoEntity.setId(request.getProyectoId());

        Inversion inv = new Inversion();
        inv.setUsuario(usuario);
        inv.setProyecto(proyectoEntity);
        inv.setMontoIdea(request.getMontoIdea());
        inv.setSubTokensRecibidos(subTokens);
        inv.setTxHash(txHash);
        inv.setEstado("CONFIRMADA");
        inv.setDescuentoPorcentaje(descuentoPorcentaje != null ? descuentoPorcentaje.intValue() : null);

        Inversion savedInv = investmentRepository.save(inv);

        // Airdrop de Fidelidad eliminado. Se aplica directamente como descuento.
        eventPublisher.publishEvent(new InvestmentConfirmedEvent(
            usuarioId, request.getProyectoId(), request.getMontoIdea(), subTokens, txHash));

        return toResponse(savedInv);
    }

    public Page<InvestmentResponse> getInvestmentHistory(Long usuarioId, Pageable pageable) {
        Page<Inversion> inversiones = investmentRepository.findByUsuarioId(usuarioId, pageable);
        Set<Long> proyectoIds = inversiones.stream()
            .map(i -> i.getProyecto().getId())
            .collect(Collectors.toSet());
        Map<Long, Map<String, String>> proyectoInfo = buildProyectoInfoMap(proyectoIds);
        return inversiones.map(inv -> toResponse(inv, proyectoInfo));
    }

    private Map<Long, Map<String, String>> buildProyectoInfoMap(Set<Long> proyectoIds) {
        if (proyectoIds.isEmpty()) return Map.of();
        String ids = proyectoIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT id, titulo, estado FROM projects WHERE id IN (" + ids + ")",
            (rs, rowNum) -> {
                java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("titulo", rs.getString("titulo"));
                m.put("estado", rs.getString("estado"));
                return m;
            }
        );
        return rows.stream()
            .collect(Collectors.toMap(
                r -> (Long) r.get("id"),
                r -> Map.of("titulo", (String) r.get("titulo"), "estado", (String) r.get("estado"))
            ));
    }

    public InvestmentResponse getInvestmentById(Long id) {
        return toResponse(findInvestmentOrThrow(id));
    }

    @Transactional
    public void processExpiredProjects() {
        List<Map<String, Object>> expiredProjects = jdbc.query(
            "SELECT id, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado " +
            "FROM projects WHERE estado = 'FINANCIAMIENTO' AND plazo IS NOT NULL " +
            "AND plazo < NOW() AND deleted_at IS NULL",
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "montoRequerido", rs.getBigDecimal("monto_requerido"),
                "montoRecaudado", rs.getBigDecimal("monto_recaudado")
            )
        );

        for (Map<String, Object> project : expiredProjects) {
            Long projectId = (Long) project.get("id");
            BigDecimal montoRequerido = (BigDecimal) project.get("montoRequerido");
            BigDecimal montoRecaudado = (BigDecimal) project.get("montoRecaudado");

            if (montoRecaudado.compareTo(montoRequerido) < 0) {
                refundAllInvestors(projectId);
                jdbc.update("UPDATE projects SET estado = 'RECHAZADO', updated_at = NOW() WHERE id = ?", projectId);
                eventPublisher.publishEvent(new ProjectStateChangedEvent(
                    projectId, "FINANCIAMIENTO", "RECHAZADO", null));
            } else {
                jdbc.update("UPDATE projects SET estado = 'EJECUCION', updated_at = NOW() WHERE id = ?", projectId);
                eventPublisher.publishEvent(new ProjectStateChangedEvent(
                    projectId, "FINANCIAMIENTO", "EJECUCION", null));
            }
        }
    }

    @Transactional
    public void refundAllInvestors(Long proyectoId) {
        List<Inversion> inversiones = investmentRepository.findPendingRefundsByProyectoId(proyectoId);

        BigDecimal precioBase = jdbc.queryForObject(
            "SELECT precio_base FROM subtokens WHERE proyecto_id = ?", BigDecimal.class, proyectoId
        );

        for (Inversion inv : inversiones) {
            int tokens = inv.getSubTokensRecibidos() != null ? inv.getSubTokensRecibidos() : 0;
            BigDecimal refundAmount = precioBase.multiply(BigDecimal.valueOf(tokens));

            Map<String, Object> result = smartContractService.refundInvestment(
                proyectoId, inv.getUsuario().getId(), refundAmount);

            boolean success = (boolean) result.getOrDefault("success", false);
            if (!success) {
                throw new ConflictException("Refund on-chain fallo para inversion " + inv.getId()
                    + " proyecto " + proyectoId + ". Rollback de refunds del proyecto.");
            }

            inv.setTxHash((String) result.get("refundTxHash"));
            jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
                refundAmount, inv.getUsuario().getId());

            if (tokens > 0) {
                subtokenService.removePortfolioEntry(
                    inv.getUsuario().getId(), proyectoId, tokens
                );
            }

            inv.setEstado("REEMBOLSADA");
            investmentRepository.save(inv);
        }
    }

    private Inversion findInvestmentOrThrow(Long id) {
        return investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inversion no encontrada con ID: " + id));
    }

    private Map<String, Object> findProjectRowOrThrow(Long projectId) {
        List<Map<String, Object>> results = jdbc.query(
            "SELECT id, titulo, estado, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado " +
            "FROM projects WHERE id = ? AND deleted_at IS NULL",
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "titulo", rs.getString("titulo"),
                "estado", rs.getString("estado"),
                "montoRequerido", rs.getBigDecimal("monto_requerido"),
                "montoRecaudado", rs.getBigDecimal("monto_recaudado")
            ),
            projectId
        );

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + projectId);
        }
        return results.get(0);
    }

    private BigDecimal obtenerMontoRecaudadoOnChain(Long proyectoId) {
        Map<String, Object> proyecto = findProjectRowOrThrow(proyectoId);
        return (BigDecimal) proyecto.get("montoRecaudado");
    }

    private InvestmentResponse toResponse(Inversion inv) {
        String proyectoTitulo = "Proyecto #" + inv.getProyecto().getId();
        String proyectoEstado = null;
        try {
            List<Map<String, Object>> rows = jdbc.query(
                "SELECT titulo, estado FROM projects WHERE id = ?",
                (rs, rowNum) -> {
                    java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                    m.put("titulo", rs.getString("titulo"));
                    m.put("estado", rs.getString("estado"));
                    return m;
                },
                inv.getProyecto().getId()
            );
            if (!rows.isEmpty()) {
                proyectoTitulo = (String) rows.get(0).get("titulo");
                proyectoEstado = (String) rows.get(0).get("estado");
            }
        } catch (Exception ignored) {}
        return toResponse(inv, proyectoTitulo, proyectoEstado);
    }

    private InvestmentResponse toResponse(Inversion inv, Map<Long, Map<String, String>> proyectoInfo) {
        Map<String, String> info = proyectoInfo.getOrDefault(inv.getProyecto().getId(), Map.of());
        String proyectoTitulo = info.getOrDefault("titulo", "Proyecto #" + inv.getProyecto().getId());
        String proyectoEstado = info.get("estado");
        return toResponse(inv, proyectoTitulo, proyectoEstado);
    }

    private InvestmentResponse toResponse(Inversion inv, String proyectoTitulo, String proyectoEstado) {
        BigDecimal precioSubtoken = null;
        if (inv.getMontoIdea() != null && inv.getSubTokensRecibidos() != null && inv.getSubTokensRecibidos() > 0) {
            precioSubtoken = inv.getMontoIdea()
                .divide(BigDecimal.valueOf(inv.getSubTokensRecibidos()), 2, RoundingMode.HALF_UP);
        }

        return InvestmentResponse.builder()
                .id(inv.getId())
                .usuarioId(inv.getUsuario() != null ? inv.getUsuario().getId() : null)
                .proyectoId(inv.getProyecto() != null ? inv.getProyecto().getId() : null)
                .proyectoTitulo(proyectoTitulo)
                .proyectoEstado(proyectoEstado)
                .montoIdea(inv.getMontoIdea())
                .subTokensRecibidos(inv.getSubTokensRecibidos())
                .precioSubtoken(precioSubtoken)
                .txHash(inv.getTxHash())
                .estado(inv.getEstado())
                .descuentoPorcentaje(inv.getDescuentoPorcentaje() != null ? new java.math.BigDecimal(inv.getDescuentoPorcentaje()) : null)
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    private BigDecimal getCrossRewardDiscountPercentage(Long usuarioId, Long proyectoId) {
        try {
            Long creadorId = jdbc.queryForObject(
                "SELECT creador_id FROM projects WHERE id = ?", Long.class, proyectoId
            );
            
            // Contamos en cuántos proyectos DISTINTOS de este creador invirtió antes
            Integer proyectosPrevios = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT p.id) FROM investments i " +
                "JOIN projects p ON i.proyecto_id = p.id " +
                "WHERE i.usuario_id = ? AND p.creador_id = ? AND i.estado = 'CONFIRMADA'",
                Integer.class, usuarioId, creadorId
            );
            if (proyectosPrevios == null) proyectosPrevios = 0;
            
            // Regla: La inversion que desbloquea el logro ya recibe el beneficio
            int totalProyectos = proyectosPrevios + 1;
            
            if (totalProyectos >= 2) {
                BigDecimal baseDiscount = new BigDecimal("5.0");
                
                // 1. Multiplicador por Nivel de Inversor
                String nivelInversor = null;
                try {
                    nivelInversor = jdbc.queryForObject(
                        "SELECT nivel_inversor FROM users WHERE id = ?", String.class, usuarioId
                    );
                } catch (Exception ignored) { }
                
                BigDecimal multiplierLevel = BigDecimal.ONE;
                if (nivelInversor != null) {
                    switch(nivelInversor.toUpperCase()) {
                        case "INVESTOR": multiplierLevel = new BigDecimal("1.5"); break; // 7.5%
                        case "PARTNER": multiplierLevel = new BigDecimal("2.0"); break;  // 10.0%
                        case "VISIONARY": multiplierLevel = new BigDecimal("3.0"); break; // 15.0%
                        default: multiplierLevel = BigDecimal.ONE; break; // STARTER: 5.0%
                    }
                }
                
                // 2. Multiplicador por Set Bonus NFT
                BigDecimal multiplierSetBonus = BigDecimal.ONE;
                if (totalProyectos >= 5) {
                    multiplierSetBonus = new BigDecimal("1.75"); // Arquitecto IDEAFY
                } else if (totalProyectos >= 3) {
                    multiplierSetBonus = new BigDecimal("1.5");  // Trilogía IDEAFY
                } else if (totalProyectos >= 2) {
                    multiplierSetBonus = new BigDecimal("1.25"); // Duo IDEAFY
                }
                
                // 3. Regla Opción B (El Mayor Gana)
                BigDecimal finalMultiplier = multiplierLevel.max(multiplierSetBonus);
                
                return baseDiscount.multiply(finalMultiplier);
            }
        } catch (Exception e) {
            log.warn("Error calculando descuento cross-reward: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }
}
