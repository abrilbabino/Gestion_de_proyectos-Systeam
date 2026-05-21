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

import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.config.BlockchainProperties;
import com.systeam.investment.dto.CreateInvestmentRequest;
import com.systeam.investment.dto.InvestmentResponse;
import com.systeam.investment.dto.ValidateInvestmentRequest;
import com.systeam.investment.dto.ValidateInvestmentResponse;
import com.systeam.investment.repository.InvestmentRepository;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.service.SmartContractService;
import com.systeam.shared.model.Inversion;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

@Service
public class InvestmentService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);

    private final InvestmentRepository investmentRepository;
    private final SmartContractService smartContractService;
    private final InvestmentSwapService investmentSwapService;
    private final BlockchainProperties blockchainProperties;
    private final JdbcTemplate jdbc;
    private final DynamicPricingService pricingService;

    public InvestmentService(InvestmentRepository investmentRepository,
                             SmartContractService smartContractService,
                             InvestmentSwapService investmentSwapService,
                             BlockchainProperties blockchainProperties,
                             JdbcTemplate jdbc,
                             DynamicPricingService pricingService) {
        this.investmentRepository = investmentRepository;
        this.smartContractService = smartContractService;
        this.investmentSwapService = investmentSwapService;
        this.blockchainProperties = blockchainProperties;
        this.jdbc = jdbc;
        this.pricingService = pricingService;
    }

    public ValidateInvestmentResponse validateInvestment(ValidateInvestmentRequest request) {
        Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
        String estado = (String) proyecto.get("estado");

        if (!"FINANCIAMIENTO".equals(estado)) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El proyecto no esta en estado de financiamiento")
                    .build();
        }

        Map<String, Object> subtoken = findSubtokenByProject(request.getProyectoId());
        if (subtoken == null) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El proyecto no tiene un subtoken asociado")
                    .build();
        }

        int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
        BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
        int suministroTotal = (int) subtoken.get("suministro_total");
        BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

        BigDecimal precioSubtoken = pricingService.calcularPrecioDinamico(
            precioBase, suministroTotal, cupoRestante, factorVolatilidad
        );

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
                    .build();
        }

        if (subTokensNecesarios > cupoRestante) {
            return ValidateInvestmentResponse.builder()
                    .valido(false)
                    .mensaje("El monto solicitado supera el cupo disponible del proyecto")
                    .cupoDisponible(cupoRestante)
                    .precioSubtoken(precioSubtoken)
                    .subTokensARecebir(subTokensNecesarios)
                    .build();
        }

        return ValidateInvestmentResponse.builder()
                .valido(true)
                .mensaje("Inversion valida")
                .cupoDisponible(cupoRestante)
                .precioSubtoken(precioSubtoken)
                .subTokensARecebir(subTokensNecesarios)
                .build();
    }

    @Transactional
    public InvestmentResponse createInvestment(CreateInvestmentRequest request, Long usuarioId) {
        Map<String, Object> proyecto = findProjectRowOrThrow(request.getProyectoId());
        String estado = (String) proyecto.get("estado");

        if (!"FINANCIAMIENTO".equals(estado)) {
            throw new ConflictException("El proyecto no esta en estado de financiamiento");
        }

        Map<String, Object> subtoken = findSubtokenByProject(request.getProyectoId());
        if (subtoken == null) {
            throw new ConflictException("El proyecto no tiene un subtoken asociado para invertir");
        }

        Long subtokenId = ((Number) subtoken.get("id")).longValue();
        int cupoRestante = ((Number) subtoken.get("cupo_restante")).intValue();
        BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
        int suministroTotal = (int) subtoken.get("suministro_total");
        BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

        BigDecimal precioSubtoken = pricingService.calcularPrecioDinamico(
            precioBase, suministroTotal, cupoRestante, factorVolatilidad
        );

        int subTokens = request.getMontoIdea()
                .divide(precioSubtoken, 0, RoundingMode.DOWN)
                .intValue();

        if (subTokens <= 0) {
            throw new ConflictException("El monto es insuficiente para recibir al menos 1 sub-token");
        }

        if (subTokens > cupoRestante) {
            throw new ConflictException("El monto solicitado supera el cupo disponible del proyecto");
        }

        BigDecimal saldoActual = jdbc.queryForObject(
            "SELECT saldo_idea FROM users WHERE id = ?", BigDecimal.class, usuarioId
        );

        if (saldoActual == null || saldoActual.compareTo(request.getMontoIdea()) < 0) {
            throw new ConflictException("Saldo insuficiente de tokens IDEA");
        }

        String txHash;
        boolean swapOnChain = false;
        try {
            BigDecimal montoIdeaWei = request.getMontoIdea()
                .multiply(new BigDecimal("1000000000000000000"));
            BigDecimal subTokenAmountWei = BigDecimal.valueOf(subTokens)
                .multiply(new BigDecimal("1000000000000000000"));

            String treasuryAddress = blockchainProperties.getTreasuryAddress();

            txHash = investmentSwapService.invest(
                request.getProyectoId(),
                montoIdeaWei.toBigInteger(),
                subTokenAmountWei.toBigInteger(),
                treasuryAddress
            );
            swapOnChain = true;
            log.info("INV-05: Swap on-chain exitoso. Tx: {}", txHash);
        } catch (Exception e) {
            log.warn("INV-05: Swap on-chain no disponible. Fallback a DB-only. Error: {}", e.getMessage());
            Map<String, Object> txResult = smartContractService.recordInvestment(
                request.getProyectoId(), usuarioId, request.getMontoIdea(), request.getTxHash()
            );
            txHash = (String) txResult.get("txHash");
        }

        jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?",
            request.getMontoIdea(), usuarioId);

        jdbc.update("UPDATE projects SET monto_recaudado = COALESCE(monto_recaudado, 0) + ? WHERE id = ?",
            request.getMontoIdea(), request.getProyectoId());

        int nuevoCupo = cupoRestante - subTokens;
        BigDecimal nuevoPrecio = pricingService.calcularPrecioDinamico(
            precioBase, suministroTotal, nuevoCupo, factorVolatilidad
        );
        jdbc.update("UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ? WHERE id = ?",
            subTokens, nuevoPrecio, subtokenId);

        jdbc.update("""
            INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad, created_at, updated_at)
            VALUES (?, ?, ?, NOW(), NOW())
            ON CONFLICT (usuario_id, subtoken_id)
            DO UPDATE SET cantidad = portfolio_activos.cantidad + ?, updated_at = NOW()
            """, usuarioId, subtokenId, subTokens, subTokens);

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

        return toResponse(investmentRepository.save(inv));
    }

    public Page<InvestmentResponse> getInvestmentHistory(Long usuarioId, Pageable pageable) {
        Page<Inversion> inversiones = investmentRepository.findByUsuarioId(usuarioId, pageable);
        Set<Long> proyectoIds = inversiones.stream()
            .map(i -> i.getProyecto().getId())
            .collect(Collectors.toSet());
        Map<Long, String> titulos = buildTituloMap(proyectoIds);
        return inversiones.map(inv -> toResponse(inv, titulos));
    }

    private Map<Long, String> buildTituloMap(Set<Long> proyectoIds) {
        if (proyectoIds.isEmpty()) return Map.of();
        String ids = proyectoIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT id, titulo FROM projects WHERE id IN (" + ids + ")",
            (rs, rowNum) -> Map.of("id", rs.getLong("id"), "titulo", rs.getString("titulo"))
        );
        return rows.stream()
            .collect(Collectors.toMap(r -> (Long) r.get("id"), r -> (String) r.get("titulo")));
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
            }
        }
    }

    @Transactional
    public void refundAllInvestors(Long proyectoId) {
        List<Inversion> inversiones = investmentRepository.findPendingRefundsByProyectoId(proyectoId);

        for (Inversion inv : inversiones) {
            smartContractService.refundInvestment(proyectoId, inv.getUsuario().getId(), inv.getMontoIdea());

            jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
                inv.getMontoIdea(), inv.getUsuario().getId());

            if (inv.getSubTokensRecibidos() != null && inv.getSubTokensRecibidos() > 0) {
                jdbc.update("""
                    UPDATE portfolio_activos pa
                    SET cantidad = GREATEST(pa.cantidad - ?, 0), updated_at = NOW()
                    FROM subtokens s
                    WHERE pa.subtoken_id = s.id AND s.proyecto_id = ? AND pa.usuario_id = ?
                    """, inv.getSubTokensRecibidos(), proyectoId, inv.getUsuario().getId());
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

    private Map<String, Object> findSubtokenByProject(Long proyectoId) {
        List<Map<String, Object>> results = jdbc.query(
            "SELECT id, cupo_restante, precio_actual, precio_base, suministro_total, " +
            "factor_volatilidad, contract_address " +
            "FROM subtokens WHERE proyecto_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("cupo_restante", rs.getInt("cupo_restante"));
                m.put("precio_actual", rs.getBigDecimal("precio_actual"));
                m.put("precio_base", rs.getBigDecimal("precio_base"));
                m.put("suministro_total", rs.getInt("suministro_total"));
                m.put("factor_volatilidad", rs.getBigDecimal("factor_volatilidad"));
                m.put("contract_address", rs.getString("contract_address"));
                return m;
            },
            proyectoId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private InvestmentResponse toResponse(Inversion inv) {
        String proyectoTitulo = "";
        try {
            proyectoTitulo = jdbc.queryForObject(
                "SELECT titulo FROM projects WHERE id = ?", String.class, inv.getProyecto().getId()
            );
        } catch (Exception e) {
            proyectoTitulo = "Proyecto #" + inv.getProyecto().getId();
        }
        return toResponse(inv, proyectoTitulo);
    }

    private InvestmentResponse toResponse(Inversion inv, Map<Long, String> titulos) {
        String proyectoTitulo = titulos.getOrDefault(inv.getProyecto().getId(),
            "Proyecto #" + inv.getProyecto().getId());
        return toResponse(inv, proyectoTitulo);
    }

    private InvestmentResponse toResponse(Inversion inv, String proyectoTitulo) {
        return InvestmentResponse.builder()
                .id(inv.getId())
                .usuarioId(inv.getUsuario() != null ? inv.getUsuario().getId() : null)
                .proyectoId(inv.getProyecto() != null ? inv.getProyecto().getId() : null)
                .proyectoTitulo(proyectoTitulo)
                .montoIdea(inv.getMontoIdea())
                .subTokensRecibidos(inv.getSubTokensRecibidos())
                .txHash(inv.getTxHash())
                .estado(inv.getEstado())
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
