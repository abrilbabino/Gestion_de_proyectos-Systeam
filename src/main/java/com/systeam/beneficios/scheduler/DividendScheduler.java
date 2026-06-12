package com.systeam.beneficios.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.systeam.beneficios.service.DividendService;
import com.systeam.project.exception.OracleBillingNotFoundException;

@Component
public class DividendScheduler {

    private static final Logger log = LoggerFactory.getLogger(DividendScheduler.class);

    private final DividendService dividendService;
    private final JdbcTemplate jdbc;

    public DividendScheduler(DividendService dividendService, JdbcTemplate jdbc) {
        this.dividendService = dividendService;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 1 * ?")
    public void distributeMonthlyDividends() {
        log.info("Iniciando distribucion mensual de dividendos...");

        List<Long> proyectos = jdbc.queryForList(
            "SELECT p.id FROM projects p WHERE p.estado IN ('EJECUCION', 'FINALIZADO')" +
            " AND p.deleted_at IS NULL" +
            " AND NOT EXISTS (" +
            "   SELECT 1 FROM dividendos d" +
            "   WHERE d.proyecto_id = p.id" +
            "   AND DATE_TRUNC('month', d.fecha_reparto) = DATE_TRUNC('month', NOW())" +
            " )",
            Long.class
        );

        if (proyectos.isEmpty()) {
            log.info("No hay proyectos elegibles para dividendos este mes");
            return;
        }

        for (Long proyectoId : proyectos) {
            try {
                Long dividendoId = dividendService.crearRepartoDesdeOraculo(proyectoId);
                log.info("Dividendos distribuidos: proyecto={}, dividendoId={}", proyectoId, dividendoId);
            } catch (OracleBillingNotFoundException e) {
                log.warn("Sin datos de oraculo para proyecto {}: {}", proyectoId, e.getMessage());
            } catch (Exception e) {
                log.error("Error distribuyendo dividendos para proyecto {}: {}", proyectoId, e.getMessage());
            }
        }

        log.info("Distribucion mensual de dividendos finalizada");
    }
}
