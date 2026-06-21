package com.systeam.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.investment.service.SmartContractService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;

@Service
public class BoostService {

    private static final Logger log = LoggerFactory.getLogger(BoostService.class);
    private static final BigDecimal COSTO_BOOST = new BigDecimal("100.00");
    private static final int DIAS_VIGENCIA = 7;

    private final JdbcTemplate jdbc;
    private final SmartContractService smartContractService;

    public BoostService(JdbcTemplate jdbc, SmartContractService smartContractService) {
        this.jdbc = jdbc;
        this.smartContractService = smartContractService;
    }

    public void boostProject(Long proyectoId, Long usuarioId, String txHash) {
        String estado = jdbc.queryForObject(
            "SELECT estado FROM projects WHERE id = ? AND deleted_at IS NULL",
            String.class, proyectoId
        );

        if (estado == null) {
            throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + proyectoId);
        }
        if (!"FINANCIAMIENTO".equals(estado)) {
            throw new ConflictException("Solo se pueden destacar proyectos que estan en estado FINANCIAMIENTO");
        }

        // Evitar ataque de replay (usar el mismo txHash dos veces)
        Integer txHashCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM project_boosts WHERE tx_hash = ?", Integer.class, txHash
        );
        if (txHashCount != null && txHashCount > 0) {
            throw new ConflictException("La transaccion de boost ya fue registrada");
        }

        // Validar en blockchain (SmartContractService)
        if (!smartContractService.verifyBoostTransfer(txHash)) {
            throw new ConflictException("Error: La transaccion on-chain no fue encontrada o falló");
        }

        BigDecimal multiplier = getGamificationMultiplier(usuarioId);
        BigDecimal montoBoostFinal = COSTO_BOOST.multiply(multiplier);

        executeBoostDbUpdate(proyectoId, usuarioId, txHash, montoBoostFinal);

        log.info("Proyecto {} boosteado por usuario {}. Costo: {} $IDEA on-chain mediante tx {}. Puntos sumados: {}",
                proyectoId, usuarioId, COSTO_BOOST, txHash, montoBoostFinal);
    }

    private BigDecimal getGamificationMultiplier(Long usuarioId) {
        try {
            String nivelInversor = jdbc.queryForObject(
                "SELECT nivel_inversor FROM users WHERE id = ?", String.class, usuarioId
            );
            
            if (nivelInversor != null) {
                switch(nivelInversor.toUpperCase()) {
                    case "INVESTOR": return new BigDecimal("1.25");
                    case "PARTNER": return new BigDecimal("2.0");
                    case "VISIONARY": return new BigDecimal("3.0");
                }
            }
        } catch (Exception e) {
            log.warn("Error obteniendo nivel inversor para boost multiplier: {}", e.getMessage());
        }
        return BigDecimal.ONE; // STARTER o default
    }

    @Transactional
    protected void executeBoostDbUpdate(Long proyectoId, Long usuarioId, String txHash, BigDecimal montoBoostFinal) {
        // Insertar registro inmutable de la compra del boost
        jdbc.update("""
            INSERT INTO project_boosts (proyecto_id, usuario_id, tx_hash, monto_gastado, created_at)
            VALUES (?, ?, ?, ?, NOW())
            """, proyectoId, usuarioId, txHash, COSTO_BOOST);

        jdbc.update("""
            UPDATE projects SET es_destacado = TRUE, fecha_boost = NOW(),
                monto_boost = monto_boost + ?, updated_at = NOW()
            WHERE id = ? AND deleted_at IS NULL
            """, montoBoostFinal, proyectoId);
    }

    @Transactional
    public int applyDecayToBoosts() {
        return jdbc.update("UPDATE projects SET monto_boost = monto_boost * 0.90 WHERE monto_boost > 0 AND deleted_at IS NULL");
    }
}
