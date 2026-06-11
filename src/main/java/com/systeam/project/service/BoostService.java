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

    public void boostProject(Long proyectoId, Long usuarioId) {
        String estado = jdbc.queryForObject(
            "SELECT estado FROM projects WHERE id = ? AND deleted_at IS NULL",
            String.class, proyectoId
        );

        if (estado == null) {
            throw new ResourceNotFoundException("Proyecto no encontrado con ID: " + proyectoId);
        }

        Boolean yaDestacado = jdbc.queryForObject(
            "SELECT es_destacado FROM projects WHERE id = ?", Boolean.class, proyectoId
        );
        if (Boolean.TRUE.equals(yaDestacado)) {
            throw new ConflictException("El proyecto ya está destacado");
        }

        BigDecimal saldo = jdbc.queryForObject(
            "SELECT saldo_idea FROM users WHERE id = ?", BigDecimal.class, usuarioId
        );

        if (saldo == null || saldo.compareTo(COSTO_BOOST) < 0) {
            throw new ConflictException("Saldo insuficiente de tokens IDEA. Se necesitan " + COSTO_BOOST + " $IDEA");
        }

        // Blockchain first — send 100 IDEA to fee address and verify
        Map<String, Object> result = smartContractService.boostProject();
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new ConflictException("Error en boost on-chain: " + result.get("note"));
        }

        executeBoostDbUpdate(proyectoId, usuarioId);

        log.info("Proyecto {} boosteado por usuario {}. Costo: {} $IDEA on-chain. Vigencia: {} dias",
                proyectoId, usuarioId, COSTO_BOOST, DIAS_VIGENCIA);
    }

    @Transactional
    protected void executeBoostDbUpdate(Long proyectoId, Long usuarioId) {
        jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?", COSTO_BOOST, usuarioId);

        jdbc.update("""
            UPDATE projects SET es_destacado = TRUE, fecha_boost = NOW(),
                monto_boost = monto_boost + ?, updated_at = NOW()
            WHERE id = ? AND deleted_at IS NULL
            """, COSTO_BOOST, proyectoId);
    }

    public void desboostProject(Long proyectoId) {
        jdbc.update("UPDATE projects SET es_destacado = FALSE WHERE id = ? AND deleted_at IS NULL", proyectoId);
    }
}
