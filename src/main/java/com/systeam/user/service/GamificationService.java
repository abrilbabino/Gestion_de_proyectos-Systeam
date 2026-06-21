package com.systeam.user.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.InvestmentConfirmedEvent;
import com.systeam.user.model.InvestorLevel;

@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);
    private final JdbcTemplate jdbc;

    public GamificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvestmentConfirmed(InvestmentConfirmedEvent event) {
        log.info("InvestmentConfirmedEvent recibido para usuario {}. Recalculando nivel de gamificación...", event.getInversorId());
        calculateAndSetUserLevel(event.getInversorId());
    }

    @Transactional
    public InvestorLevel calculateAndSetUserLevel(Long usuarioId) {
        // 1. Proyectos fondeados
        Integer proyectosFondeados = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT proyecto_id) FROM investments WHERE usuario_id = ? AND estado = 'CONFIRMADA'",
            Integer.class, usuarioId
        );
        if (proyectosFondeados == null) proyectosFondeados = 0;

        // 2. Creadores distintos
        Integer creadoresDistintos = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT p.creator_id) FROM investments i " +
            "JOIN projects p ON i.proyecto_id = p.id " +
            "WHERE i.usuario_id = ? AND i.estado = 'CONFIRMADA'",
            Integer.class, usuarioId
        );
        if (creadoresDistintos == null) creadoresDistintos = 0;

        // 3. Monto total invertido
        BigDecimal montoTotalInvertido = jdbc.queryForObject(
            "SELECT COALESCE(SUM(monto_idea), 0) FROM investments WHERE usuario_id = ? AND estado = 'CONFIRMADA'",
            BigDecimal.class, usuarioId
        );
        if (montoTotalInvertido == null) montoTotalInvertido = BigDecimal.ZERO;

        // Determinar el nivel basandose en la matriz (Canva)
        InvestorLevel newLevel = InvestorLevel.STARTER; // Default

        if (proyectosFondeados >= 15 && creadoresDistintos >= 10 && montoTotalInvertido.compareTo(new BigDecimal("50000")) >= 0) {
            newLevel = InvestorLevel.VISIONARY;
        } else if (proyectosFondeados >= 7 && creadoresDistintos >= 5 && montoTotalInvertido.compareTo(new BigDecimal("10000")) >= 0) {
            newLevel = InvestorLevel.PARTNER;
        } else if (proyectosFondeados >= 3 && creadoresDistintos >= 2 && montoTotalInvertido.compareTo(new BigDecimal("2000")) >= 0) {
            newLevel = InvestorLevel.INVESTOR;
        }

        // Actualizar en BD
        jdbc.update("UPDATE users SET nivel_inversor = ? WHERE id = ?", newLevel.name(), usuarioId);

        log.info("Gamification recalculada para usuario {}: Proyectos={}, Creadores={}, Monto={}, NuevoNivel={}", 
            usuarioId, proyectosFondeados, creadoresDistintos, montoTotalInvertido, newLevel);

        return newLevel;
    }
}
