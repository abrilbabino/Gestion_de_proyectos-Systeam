package com.systeam.beneficios.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.DividendDistributorService;
import com.systeam.blockchain.service.IdeaSwapService;
import com.systeam.notificaciones.event.DividendDistributedEvent;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.OracleBillingNotFoundException;
import com.systeam.project.exception.ResourceNotFoundException;

@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);

    private final JdbcTemplate jdbc;
    private final DividendDistributorService dividendDistributorService;
    private final BlockchainService blockchainService;
    private final IdeaSwapService ideaSwapService;
    private final ApplicationEventPublisher eventPublisher;

    public DividendService(JdbcTemplate jdbc, DividendDistributorService dividendDistributorService,
                           BlockchainService blockchainService, IdeaSwapService ideaSwapService,
                           ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.dividendDistributorService = dividendDistributorService;
        this.blockchainService = blockchainService;
        this.ideaSwapService = ideaSwapService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long crearReparto(Long proyectoId, BigDecimal montoTotal) {
        String estado = jdbc.queryForObject(
            "SELECT estado FROM projects WHERE id = ? AND deleted_at IS NULL",
            String.class, proyectoId
        );

        if (estado == null) {
            throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + proyectoId);
        }

        if (!"EJECUCION".equals(estado) && !"FINALIZADO".equals(estado)) {
            throw new ConflictException(
                "Solo se pueden repartir dividendos en proyectos en EJECUCION o FINALIZADO"
            );
        }

        // Blockchain first — distribute on-chain and verify before saving in DB
        String txHash;
        try {
            BigInteger montoWei = montoTotal
                .multiply(new BigDecimal("1000000000000000000"))
                .toBigInteger();
            txHash = dividendDistributorService.distribute(
                BigInteger.valueOf(proyectoId), montoWei
            );
            if (!verifyBlockchainTx(txHash)) {
                throw new ConflictException("La distribucion de dividendos on-chain fallo en Sepolia");
            }
            log.info("Dividendos distribuidos on-chain: proyecto={}, tx={}", proyectoId, txHash);
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException("Error al distribuir dividendos on-chain: " + e.getMessage());
        }

        // Save in DB as historical record
        Integer suministroTotal = jdbc.queryForObject(
            "SELECT suministro_total FROM subtokens WHERE proyecto_id = ?",
            Integer.class, proyectoId
        );

        if (suministroTotal == null || suministroTotal <= 0) {
            throw new ConflictException("No hay suministro de subtokens para este proyecto");
        }

        // El Smart Contract retiene un 5% de comision (DISTRIBUTION_FEE_BPS = 500)
        BigDecimal fee = montoTotal.multiply(new BigDecimal("0.05"));
        BigDecimal montoNeto = montoTotal.subtract(fee);

        BigDecimal montoPorSubtoken = montoNeto.divide(
            BigDecimal.valueOf(suministroTotal), 4, RoundingMode.HALF_UP
        );

        Long dividendoId = jdbc.queryForObject("""
            INSERT INTO dividendos (proyecto_id, monto_total, monto_por_subtoken, estado, tx_hash, fecha_reparto, created_at)
            VALUES (?, ?, ?, 'DISTRIBUIDO', ?, NOW(), NOW())
            RETURNING id
            """, Long.class, proyectoId, montoTotal, montoPorSubtoken, txHash
        );

        log.info("Reparto de dividendos creado: id={}, proyecto={}, total={}, porSubtoken={}",
                dividendoId, proyectoId, montoTotal, montoPorSubtoken);

        return dividendoId;
    }

    @Transactional
    public Long crearRepartoDesdeOraculo(Long proyectoId) {
        Map<String, Object> billing;
        try {
            billing = jdbc.queryForObject(
                "SELECT monto_facturado, fecha_reporte, oracle_address, tx_hash " +
                "FROM oracle_billing WHERE proyecto_id = ? ORDER BY fecha_reporte DESC LIMIT 1",
                (rs, rowNum) -> Map.of(
                    "montoFacturado", rs.getBigDecimal("monto_facturado"),
                    "fechaReporte",   rs.getTimestamp("fecha_reporte").toLocalDateTime(),
                    "oracleAddress",  rs.getString("oracle_address"),
                    "txHash",         rs.getString("tx_hash")
                ),
                proyectoId
            );
        } catch (Exception e) {
            billing = null;
        }

        if (billing == null) {
            throw new OracleBillingNotFoundException(
                "No hay datos del oráculo para el proyecto " + proyectoId +
                ". La distribución de dividendos está congelada hasta recibir validación on-chain."
            );
        }

        BigDecimal dividendBps = BigDecimal.valueOf(3000); // 30% by default, dividend_bps column doesn't exist

        BigDecimal montoFacturado = (BigDecimal) billing.get("montoFacturado");
        // dividendBps es tasa anual (ej: 2000 = 20% anual). Dividimos /12 para tasa mensual.
        BigDecimal montoRepartoUsdc = montoFacturado
            .multiply(dividendBps)
            .divide(BigDecimal.valueOf(10000), 4, java.math.RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);

        log.info("Reparto oracle: proyecto={}, montoFacturado={}, bps={} ({}% anual), montoReparto mensual={}",
            proyectoId, montoFacturado, dividendBps,
            dividendBps.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP),
            montoRepartoUsdc);

        try {
            // 1. Convert montoRepartoUsdc to Wei
            BigInteger usdcWei = montoRepartoUsdc.multiply(BigDecimal.TEN.pow(18)).toBigIntegerExact();

            // 2. Calculate how much $IDEA we can buy with that USDC
            BigInteger ideaToBuy = ideaSwapService.getIdeaOutForExactUsdcIn(usdcWei);
            log.info("Calculado swap: con {} USDC se comprarán {} IDEA", montoRepartoUsdc, new BigDecimal(ideaToBuy).divide(BigDecimal.TEN.pow(18)));

            // 3. Execute the Swap (USDC -> IDEA) and WAIT for mining before distributing
            String swapTx = ideaSwapService.swapUsdcForExactIdeaAndWait(ideaToBuy);
            log.info("Swap confirmado on-chain. Tx: {}", swapTx);

            // 4. Distribute the recently bought $IDEA to the holders
            BigDecimal montoIdea = new BigDecimal(ideaToBuy).divide(BigDecimal.TEN.pow(18));
            return crearReparto(proyectoId, montoIdea);

        } catch (Exception e) {
            log.error("Error al ejecutar swap o reparto de dividendos: {}", e.getMessage(), e);
            throw new RuntimeException("Error en proceso de dividendos: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void reclamarDividendos(Long proyectoId, Long usuarioId, String wallet, String txHash) {
        if (!verifyBlockchainTx(txHash)) {
            throw new ConflictException("El reclamo de dividendos on-chain falló o la txHash es inválida");
        }
        log.info("Dividendos reclamados verificados on-chain: proyecto={}, wallet={}, tx={}",
            proyectoId, wallet, txHash);

        // 2. Acreditar via DB (registro histórico solamente, el Smart Contract ya envió los tokens)
        List<Map<String, Object>> activos = jdbc.query(
            "SELECT pa.subtoken_id, pa.cantidad, s.nombre " +
            "FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id " +
            "WHERE pa.usuario_id = ? AND s.proyecto_id = ? AND pa.cantidad > 0",
            (rs, rowNum) -> Map.of(
                "subtokenId", rs.getLong("subtoken_id"),
                "cantidad", rs.getInt("cantidad"),
                "nombre", rs.getString("nombre")
            ),
            usuarioId, proyectoId
        );

        if (activos.isEmpty()) {
            throw new ConflictException("No tienes subtokens en este proyecto");
        }

        BigDecimal montoPorSubtoken = jdbc.queryForObject(
            "SELECT COALESCE((SELECT monto_por_subtoken FROM dividendos WHERE proyecto_id = ? " +
            "ORDER BY created_at DESC LIMIT 1), 0)",
            BigDecimal.class, proyectoId
        );

        if (montoPorSubtoken == null || montoPorSubtoken.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("No hay dividendos registrados para este proyecto");
        }

        for (Map<String, Object> activo : activos) {
            Long subtokenId = (Long) activo.get("subtokenId");
            Integer cantidad = (Integer) activo.get("cantidad");

            BigDecimal montoRecibido = montoPorSubtoken.multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            jdbc.update("""
                INSERT INTO reclamos_dividendos (dividendo_id, usuario_id, subtoken_id,
                    cantidad_subtokens, monto_recibido, reclamado_en)
                VALUES ((SELECT id FROM dividendos WHERE proyecto_id = ? ORDER BY created_at DESC LIMIT 1),
                    ?, ?, ?, ?, NOW())
                """, proyectoId, usuarioId, subtokenId, cantidad, montoRecibido);
        }

        log.info("Dividendos reclamados: proyecto={}, usuario={}",
                proyectoId, usuarioId);

        // Calculate total monto and publish event — DividendEventListener handles email
        BigDecimal montoTotal = BigDecimal.ZERO;
        for (Map<String, Object> activo : activos) {
            Integer cantidad = (Integer) activo.get("cantidad");
            BigDecimal montoRecibido = montoPorSubtoken.multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);
            montoTotal = montoTotal.add(montoRecibido);
        }
        eventPublisher.publishEvent(new DividendDistributedEvent(proyectoId, usuarioId, montoTotal));
    }

    public BigInteger consultarDividendosPendientes(Long proyectoId, String wallet) {
        if (wallet == null || wallet.isBlank()) return BigInteger.ZERO;

        try {
            return dividendDistributorService.getClaimable(
                BigInteger.valueOf(proyectoId), wallet
            );
        } catch (Exception e) {
            log.warn("No se pudo consultar dividendos pendientes: {}", e.getMessage());
            return BigInteger.ZERO;
        }
    }

    public List<Map<String, Object>> listarRepartos(Long proyectoId) {
        return jdbc.query(
            "SELECT id, proyecto_id, monto_total, monto_por_subtoken, fecha_reparto, created_at FROM dividendos WHERE proyecto_id = ? ORDER BY fecha_reparto DESC",
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "proyectoId", rs.getLong("proyecto_id"),
                "montoTotal", rs.getBigDecimal("monto_total"),
                "montoPorSubtoken", rs.getBigDecimal("monto_por_subtoken"),
                "fechaReparto", rs.getTimestamp("fecha_reparto").toLocalDateTime(),
                "createdAt", rs.getTimestamp("created_at").toLocalDateTime()
            ),
            proyectoId
        );
    }

    public List<Map<String, Object>> listarReclamosUsuario(Long usuarioId) {
        return jdbc.query(
            "SELECT rd.*, d.proyecto_id, d.monto_total, d.monto_por_subtoken " +
            "FROM reclamos_dividendos rd " +
            "JOIN dividendos d ON rd.dividendo_id = d.id " +
            "WHERE rd.usuario_id = ? ORDER BY rd.reclamado_en DESC",
            (rs, rowNum) -> Map.of(
                "id", rs.getLong("id"),
                "dividendoId", rs.getLong("dividendo_id"),
                "proyectoId", rs.getLong("proyecto_id"),
                "subtokenId", rs.getLong("subtoken_id"),
                "cantidadSubtokens", rs.getInt("cantidad_subtokens"),
                "montoRecibido", rs.getBigDecimal("monto_recibido"),
                "reclamadoEn", rs.getTimestamp("reclamado_en").toLocalDateTime(),
                "montoTotal", rs.getBigDecimal("monto_total"),
                "montoPorSubtoken", rs.getBigDecimal("monto_por_subtoken")
            ),
            usuarioId
        );
    }

    private boolean verifyBlockchainTx(String txHash) {
        try {
            return blockchainService.verifyTransaction(txHash);
        } catch (Exception e) {
            log.error("Error verificando tx {}: {}", txHash, e.getMessage());
            return false;
        }
    }
}
