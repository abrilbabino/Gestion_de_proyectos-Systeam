package com.systeam.wallet.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {

    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal findSaldoIdea(Long userId) {
        return jdbc.queryForObject(
            "SELECT saldo_idea FROM users WHERE id = ?",
            BigDecimal.class, userId
        );
    }

    public void updateSaldoIdea(Long userId, BigDecimal balance) {
        jdbc.update("UPDATE users SET saldo_idea = ? WHERE id = ?", balance, userId);
    }

    public BigDecimal findSaldoUsdt(Long userId) {
        return jdbc.queryForObject(
            "SELECT saldo_usdt FROM users WHERE id = ?",
            BigDecimal.class, userId
        );
    }

    public List<Object[]> findPortfolio(Long userId) {
        return jdbc.query(
            "SELECT s.id AS subtoken_id, p.titulo AS proyecto_nombre, s.nombre AS subtoken_nombre, s.simbolo AS subtoken_simbolo, pa.cantidad, s.precio_actual " +
            "FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id " +
            "JOIN projects p ON s.proyecto_id = p.id " +
            "WHERE pa.usuario_id = ?",
            (rs, rowNum) -> new Object[]{
                rs.getLong("subtoken_id"),
                rs.getString("proyecto_nombre"),
                rs.getString("subtoken_nombre"),
                rs.getString("subtoken_simbolo"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_actual")
            },
            userId
        );
    }
}
