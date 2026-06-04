package com.systeam.beneficios.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.blockchain.service.DividendDistributorService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.OracleBillingNotFoundException;
import com.systeam.project.exception.ResourceNotFoundException;

@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);

    private final JdbcTemplate jdbc;
    private final DividendDistributorService dividendDistributorService;

    public DividendService(JdbcTemplate jdbc, DividendDistributorService dividendDistributorService) {
        this.jdbc = jdbc;
        this.dividendDistributorService = dividendDistributorService;
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

        // 1. Intentar distribuir on-chain via DividendDistributor
        try {
            BigInteger montoWei = montoTotal
                .multiply(new BigDecimal("1000000000000000000"))
                .toBigInteger();
            String txHash = dividendDistributorService.distribute(
                BigInteger.valueOf(proyectoId), montoWei
            );
            log.info("Dividendos distribuidos on-chain: proyecto={}, tx={}", proyectoId, txHash);
        } catch (Exception e) {
            log.warn("DividendDistributor no disponible (on-chain): {}. " +
                     "Guardando solo en DB.", e.getMessage());
        }

        // 2. Guardar en DB como registro histórico
        Integer totalSubtokensColocados = jdbc.queryForObject(
            "SELECT COALESCE(SUM(pa.cantidad), 0) FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id WHERE s.proyecto_id = ?",
            Integer.class, proyectoId
        );

        if (totalSubtokensColocados == null || totalSubtokensColocados <= 0) {
            throw new ConflictException("No hay subtokens colocados para este proyecto");
        }

        BigDecimal montoPorSubtoken = montoTotal.divide(
            BigDecimal.valueOf(totalSubtokensColocados), 4, RoundingMode.HALF_UP
        );

        Long dividendoId = jdbc.queryForObject("""
            INSERT INTO dividendos (proyecto_id, monto_total, monto_por_subtoken, fecha_reparto, created_at)
            VALUES (?, ?, ?, NOW(), NOW())
            RETURNING id
            """, Long.class, proyectoId, montoTotal, montoPorSubtoken
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

        BigDecimal dividendBps;
        try {
            Integer bps = jdbc.queryForObject(
                "SELECT dividend_bps FROM subtokens WHERE proyecto_id = ?",
                Integer.class, proyectoId
            );
            dividendBps = bps != null ? BigDecimal.valueOf(bps) : BigDecimal.valueOf(3000);
        } catch (Exception e) {
            log.warn("No se pudo obtener dividend_bps para proyecto {}: {}. Usando 30%.", proyectoId, e.getMessage());
            dividendBps = BigDecimal.valueOf(3000);
        }

        BigDecimal montoFacturado = (BigDecimal) billing.get("montoFacturado");
        BigDecimal montoReparto = montoFacturado
            .multiply(dividendBps)
            .divide(BigDecimal.valueOf(10000), 4, java.math.RoundingMode.HALF_UP);

        log.info("Reparto oracle: proyecto={}, montoFacturado={}, bps={}, montoReparto={}",
            proyectoId, montoFacturado, dividendBps, montoReparto);

        return crearReparto(proyectoId, montoReparto);
    }

    @Transactional
    public void reclamarDividendos(Long proyectoId, Long usuarioId, String wallet) {
        // 1. Intentar reclamar on-chain via DividendDistributor
        if (wallet != null && !wallet.isBlank()) {
            try {
                BigInteger claimable = dividendDistributorService.getClaimable(
                    BigInteger.valueOf(proyectoId), wallet
                );
                if (claimable.compareTo(BigInteger.ZERO) > 0) {
                    String txHash = dividendDistributorService.claim(
                        BigInteger.valueOf(proyectoId)
                    );
                    log.info("Dividendos reclamados on-chain: proyecto={}, wallet={}, tx={}",
                        proyectoId, wallet, txHash);
                } else {
                    log.info("No hay dividendos pendientes on-chain para proyecto={}, wallet={}",
                        proyectoId, wallet);
                }
            } catch (Exception e) {
                log.warn("DividendDistributor.claim() no disponible: {}. " +
                         "Usando fallback DB.", e.getMessage());
            }
        }

        // 2. Calcular y acreditar via DB (registro histórico + saldo_idea)
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
            "SELECT COALESCE(monto_por_subtoken, 0) FROM dividendos WHERE proyecto_id = ? " +
            "ORDER BY created_at DESC LIMIT 1",
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

            jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?",
                montoRecibido, usuarioId);
        }

        log.info("Dividendos reclamados: proyecto={}, usuario={}",
                proyectoId, usuarioId);
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
}
