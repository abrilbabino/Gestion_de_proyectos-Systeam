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
import com.systeam.project.config.RubroConfig;
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
                "SELECT id, monto_facturado, fecha_reporte, oracle_address, tx_hash " +
                "FROM oracle_billing WHERE proyecto_id = ? AND procesado = false ORDER BY fecha_reporte ASC LIMIT 1",
                (rs, rowNum) -> Map.of(
                  "id",             rs.getLong("id"),
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

        // Fetch rubroId from project to get the correct dividend percentage
        Integer rubroId = jdbc.queryForObject(
            "SELECT rubro FROM projects WHERE id = ?",
            Integer.class, proyectoId
        );
        if (rubroId == null) {
            rubroId = RubroConfig.getRubroIdDefault();
        }

        BigDecimal dividendBps = BigDecimal.valueOf(RubroConfig.getDividendBps(rubroId));

        BigDecimal montoFacturado = (BigDecimal) billing.get("montoFacturado");
        // El porcentaje de dividendos es una tasa anual. Dividimos /12 para obtener la cuota mensual.
        BigDecimal montoRepartoUsdc = montoFacturado
            .multiply(dividendBps)
            .divide(BigDecimal.valueOf(10000), 4, java.math.RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);

        log.info("Reparto oracle: proyecto={}, montoFacturado={}, bps={} ({}% anual), montoReparto mensual={}",
            proyectoId, montoFacturado, dividendBps, dividendBps.divide(BigDecimal.valueOf(100)), montoRepartoUsdc);

        if (montoRepartoUsdc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("El monto a repartir es 0 o menor");
        }

        // IMPORTANTE: Marcar como procesado ANTES del swap para evitar que el scheduler
        // vuelva a procesar el mismo registro si la operacion blockchain falla a mitad de camino.
        jdbc.update("UPDATE oracle_billing SET procesado = true WHERE id = ?", billing.get("id"));
        log.info("oracle_billing id={} marcado como procesado antes del swap", billing.get("id"));

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
            Long dividendoId = crearReparto(proyectoId, montoIdea);

            return dividendoId;

        } catch (Exception e) {
            log.error("Error al ejecutar swap o reparto de dividendos: {}", e.getMessage(), e);

            // Patron "exactly-once": verificar on-chain si los IDEA ya fueron asignados al contrato.
            // Si el distribute() nunca llego a la blockchain, es seguro volver a false para que
            // el scheduler lo reintente. Si los IDEA ya estan en el contrato, mantenemos true
            // para no pagar doble.
            try {
                // Consultamos el dividendPerToken del contrato: si es 0, no se distribuyo nada todavia.
                BigInteger dividendPerToken = dividendDistributorService.getDividendPerToken(BigInteger.valueOf(proyectoId));
                // Tambien chequeamos si ya habia un dividendo registrado en DB para este proyecto
                // que corresponda a este billing (si crearReparto() no llego a ejecutarse, no hay registro)
                Integer dividendosExistentes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dividendos WHERE proyecto_id = ? AND created_at >= NOW() - INTERVAL '5 minutes'",
                    Integer.class, proyectoId
                );

                boolean ideaAsignadoOnChain = dividendPerToken != null && dividendPerToken.compareTo(BigInteger.ZERO) > 0;
                boolean dividendoRegistradoEnDb = dividendosExistentes != null && dividendosExistentes > 0;

                if (!ideaAsignadoOnChain && !dividendoRegistradoEnDb) {
                    // El swap y el distribute fallaron completamente: seguro revertir a false
                    jdbc.update("UPDATE oracle_billing SET procesado = false WHERE id = ?", billing.get("id"));
                    log.warn("Rollback oracle_billing id={}: no habia IDEA asignado on-chain ni dividendo en DB. Se volvio a false para reintento.", billing.get("id"));
                } else {
                    // Los IDEA ya estan en el contrato o el dividendo fue guardado en DB: mantener true para no pagar doble
                    log.error("ATENCION: oracle_billing id={} se mantiene como procesado=true porque habia IDEA asignado on-chain (dividendPerToken={}) o dividendo en DB ({}). Revision manual requerida.",
                        billing.get("id"), dividendPerToken, dividendosExistentes);
                }
            } catch (Exception rollbackEx) {
                log.error("Error al verificar estado on-chain para rollback de oracle_billing id={}: {}. Se mantiene procesado=true por seguridad.", billing.get("id"), rollbackEx.getMessage());
            }

            throw new RuntimeException("Error en proceso de dividendos: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void reclamarDividendos(Long proyectoId, Long usuarioId, String wallet, String txHash, BigDecimal amountParam) {
        if (!verifyBlockchainTx(txHash)) {
            throw new ConflictException("El reclamo de dividendos on-chain fallo o la txHash es invalida");
        }
        log.info("Dividendos reclamados verificados on-chain: proyecto={}, wallet={}, tx={}, amountParam={}",
            proyectoId, wallet, txHash, amountParam);

        // 2. Acreditar via DB (registro historico solamente, el Smart Contract ya envio los tokens)
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

        // Obtenemos el último dividendo_id global para este proyecto
        Long ultimoDividendoId = jdbc.queryForObject(
            "SELECT id FROM dividendos WHERE proyecto_id = ? ORDER BY created_at DESC LIMIT 1",
            Long.class, proyectoId
        );

        if (ultimoDividendoId == null) {
            throw new ConflictException("No hay dividendos registrados para este proyecto");
        }

        BigDecimal montoTotal = BigDecimal.ZERO;
        for (Map<String, Object> activo : activos) {
            Long subtokenId = (Long) activo.get("subtokenId");
            Integer cantidad = (Integer) activo.get("cantidad");

            BigDecimal montoRecibido;
            if (amountParam != null) {
                // If the frontend passed the exact amount from the smart contract, use it proportionately
                // (Though usually a user only has 1 subtoken type per project, so it will just be the full amount)
                montoRecibido = amountParam;
            } else {
                BigDecimal montoPorSubtoken = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(monto_por_subtoken), 0) FROM dividendos WHERE proyecto_id = ? " +
                    "AND id > COALESCE((SELECT MAX(dividendo_id) FROM reclamos_dividendos " +
                    "WHERE usuario_id = ? AND subtoken_id = ?), 0)",
                    BigDecimal.class, proyectoId, usuarioId, subtokenId
                );
                montoRecibido = montoPorSubtoken.multiply(new BigDecimal(cantidad));
            }

            jdbc.update("""
                INSERT INTO reclamos_dividendos (dividendo_id, usuario_id, subtoken_id,
                    cantidad_subtokens, monto_recibido, reclamado_en, tx_hash)
                VALUES (?, ?, ?, ?, ?, NOW(), ?)
                """, ultimoDividendoId, usuarioId, subtokenId, cantidad, montoRecibido, txHash);
            
            montoTotal = montoTotal.add(montoRecibido);
        }

        log.info("Dividendos reclamados: proyecto={}, usuario={}, montoTotal={}",
                proyectoId, usuarioId, montoTotal);

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
            (rs, rowNum) -> {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("dividendoId", rs.getLong("dividendo_id"));
                row.put("proyectoId", rs.getLong("proyecto_id"));
                row.put("subtokenId", rs.getLong("subtoken_id"));
                row.put("cantidadSubtokens", rs.getInt("cantidad_subtokens"));
                row.put("montoRecibido", rs.getBigDecimal("monto_recibido"));
                row.put("reclamadoEn", rs.getTimestamp("reclamado_en").toLocalDateTime());
                row.put("montoTotal", rs.getBigDecimal("monto_total"));
                row.put("montoPorSubtoken", rs.getBigDecimal("monto_por_subtoken"));
                row.put("txHash", rs.getString("tx_hash"));
                return row;
            },
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
