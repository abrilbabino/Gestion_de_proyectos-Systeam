package com.systeam.tokenization.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTokenizationRepository implements TokenizationRepository {

    private final JdbcTemplate jdbc;

    public JdbcTokenizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long save(Long proyectoId, String nombre, String simbolo, int suministroTotal, BigDecimal precioBase,
                     BigDecimal factorVolatilidad, String contractAddress) {
        return jdbc.queryForObject("""
            INSERT INTO subtokens (nombre, suministro_total, cupo_restante, precio_actual,
                                   proyecto_id, precio_base, factor_volatilidad, contract_address, simbolo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class,
            nombre, suministroTotal, suministroTotal, precioBase,
            proyectoId, precioBase, factorVolatilidad, contractAddress, simbolo);
    }

    @Override
    public Optional<Map<String, Object>> findById(Long id) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT s.id, s.nombre, s.simbolo, s.suministro_total, s.cupo_restante,
                       s.precio_actual, s.precio_base, s.factor_volatilidad,
                       s.contract_address, s.proyecto_id, p.titulo AS proyectoTitulo,
                       s.created_at, s.updated_at
                FROM subtokens s
                LEFT JOIN projects p ON s.proyecto_id = p.id
                WHERE s.id = ?
                """, id);
            return Optional.of(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Map<String, Object>> findByProjectId(Long proyectoId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT s.id, s.nombre, s.simbolo, s.suministro_total, s.cupo_restante,
                       s.precio_actual, s.precio_base, s.factor_volatilidad,
                       s.contract_address, s.proyecto_id, p.titulo AS proyectoTitulo,
                       s.created_at, s.updated_at
                FROM subtokens s
                LEFT JOIN projects p ON s.proyecto_id = p.id
                WHERE s.proyecto_id = ?
                """, proyectoId);
            return Optional.of(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("""
            SELECT s.id, s.nombre, s.simbolo, s.suministro_total, s.cupo_restante,
                   s.precio_actual, s.precio_base, s.factor_volatilidad,
                   s.contract_address, s.proyecto_id, p.titulo AS proyectoTitulo,
                   s.created_at, s.updated_at
            FROM subtokens s
            LEFT JOIN projects p ON s.proyecto_id = p.id
            ORDER BY s.created_at DESC
            """);
    }

    @Override
    public Page<Map<String, Object>> findAll(Pageable pageable) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM subtokens", Long.class);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.id, s.nombre, s.simbolo, s.suministro_total, s.cupo_restante,
                   s.precio_actual, s.precio_base, s.factor_volatilidad,
                   s.contract_address, s.proyecto_id, p.titulo AS proyectoTitulo,
                   s.created_at, s.updated_at
            FROM subtokens s
            LEFT JOIN projects p ON s.proyecto_id = p.id
            ORDER BY s.created_at DESC
            LIMIT ? OFFSET ?
            """, pageable.getPageSize(), pageable.getOffset());

        return new PageImpl<>(rows, pageable, total != null ? total : 0L);
    }
}
