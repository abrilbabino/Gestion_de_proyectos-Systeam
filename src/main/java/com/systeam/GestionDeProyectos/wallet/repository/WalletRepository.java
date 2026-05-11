package com.systeam.GestionDeProyectos.wallet.repository;

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

    public BigDecimal findSaldoUsdt(Long userId) {
        return jdbc.queryForObject(
            "SELECT saldo_usdt FROM users WHERE id = ?",
            BigDecimal.class, userId
        );
    }

    public List<Object[]> findPortfolio(Long userId) {
        return jdbc.query(
            "SELECT s.nombre, pa.cantidad, s.precio_actual " +
            "FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id " +
            "WHERE pa.usuario_id = ?",
            (rs, rowNum) -> new Object[]{
                rs.getString("nombre"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_actual")
            },
            userId
        );
    }
}
