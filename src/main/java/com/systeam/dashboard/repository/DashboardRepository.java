package com.systeam.dashboard.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    public long countInvestors() {
        return jdbc.queryForObject(
            "SELECT COUNT(DISTINCT u.id) FROM users u " +
            "JOIN user_roles ur ON u.id = ur.user_id " +
            "JOIN roles r ON ur.role_id = r.id " +
            "WHERE r.name = 'INVESTOR'",
            Long.class
        );
    }

    public long countProjects() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE deleted_at IS NULL",
            Long.class
        );
    }

    public long countActiveProjects() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE deleted_at IS NULL AND estado NOT IN ('FINALIZADO', 'CANCELADO')",
            Long.class
        );
    }

    public List<Object[]> countProjectsByStatus() {
        return jdbc.query(
            "SELECT estado, COUNT(*) AS cnt FROM projects WHERE deleted_at IS NULL GROUP BY estado",
            (rs, rowNum) -> new Object[]{
                rs.getString("estado"),
                rs.getLong("cnt")
            }
        );
    }

    public BigDecimal sumRequiredAmount() {
        return jdbc.queryForObject(
            "SELECT COALESCE(SUM(monto_requerido), 0) FROM projects WHERE deleted_at IS NULL",
            BigDecimal.class
        );
    }

    public List<java.util.Map<String, Object>> getTopProjectsByInvestors() {
        return jdbc.queryForList(
            "SELECT p.id, p.titulo AS name, COUNT(DISTINCT i.usuario_id) AS inversores " +
            "FROM projects p " +
            "LEFT JOIN investments i ON p.id = i.proyecto_id " +
            "WHERE p.deleted_at IS NULL AND p.estado != 'CANCELADO' " +
            "GROUP BY p.id, p.titulo " +
            "ORDER BY inversores DESC " +
            "LIMIT 5"
        );
    }
}
