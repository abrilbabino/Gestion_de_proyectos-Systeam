package com.systeam.project.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final JdbcTemplate jdbc;

    public JdbcProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Proyecto> rowMapper = (rs, rowNum) -> {
        Proyecto p = new Proyecto();
        p.setId(rs.getLong("id"));

        Usuario creador = new Usuario();
        creador.setId(rs.getLong("creador_id"));
        p.setCreador(creador);

        p.setTitulo(rs.getString("titulo"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setMontoRequerido(rs.getBigDecimal("monto_requerido"));

        Timestamp plazo = rs.getTimestamp("plazo");
        if (plazo != null) p.setPlazo(plazo.toLocalDateTime());

        p.setEstado(rs.getString("estado"));
        p.setGobernanzaComunidad(rs.getBoolean("gobernanza_comunidad"));

        Integer cupoMaximo = rs.getObject("cupo_maximo_tokens", Integer.class);
        if (cupoMaximo != null) p.setCupoMaximoTokens(cupoMaximo);

        BigDecimal valorNominal = rs.getBigDecimal("valor_nominal_token");
        if (valorNominal != null) p.setValorNominalToken(valorNominal);

        BigDecimal montoRecaudado = rs.getBigDecimal("monto_recaudado");
        if (montoRecaudado != null) p.setMontoRecaudado(montoRecaudado);

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) p.setUpdatedAt(updatedAt.toLocalDateTime());

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) p.setDeletedAt(deletedAt.toLocalDateTime());

        p.setRubro(rs.getObject("rubro", Integer.class));

        p.setEsDestacado(rs.getBoolean("es_destacado"));

        Timestamp fechaBoost = rs.getTimestamp("fecha_boost");
        if (fechaBoost != null) p.setFechaBoost(fechaBoost.toLocalDateTime());

        BigDecimal montoBoost = rs.getBigDecimal("monto_boost");
        if (montoBoost != null) p.setMontoBoost(montoBoost);

        return p;
    };

    @Override
    public Proyecto save(Proyecto proyecto) {
        if (proyecto.getId() == null) {
            String sql = """
                INSERT INTO projects (titulo, descripcion, monto_requerido, plazo, estado,
                    gobernanza_comunidad,
                    cupo_maximo_tokens, valor_nominal_token, monto_recaudado,
                    creador_id, rubro, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                RETURNING id
                """;
            Long id = jdbc.queryForObject(sql, Long.class,
                proyecto.getTitulo(),
                proyecto.getDescripcion(),
                proyecto.getMontoRequerido(),
                toTimestamp(proyecto.getPlazo()),
                proyecto.getEstado(),
                proyecto.getGobernanzaComunidad(),
                proyecto.getCupoMaximoTokens(),
                proyecto.getValorNominalToken(),
                proyecto.getMontoRecaudado(),
                proyecto.getCreador().getId(),
                proyecto.getRubro()
            );
            proyecto.setId(id);
        } else {
            String sql = """
                UPDATE projects SET titulo=?, descripcion=?, monto_requerido=?, plazo=?,
                    estado=?, gobernanza_comunidad=?,
                    cupo_maximo_tokens=?, valor_nominal_token=?,
                    monto_recaudado=?, rubro=?, updated_at=NOW()
                WHERE id=? AND deleted_at IS NULL
                """;
            jdbc.update(sql,
                proyecto.getTitulo(),
                proyecto.getDescripcion(),
                proyecto.getMontoRequerido(),
                toTimestamp(proyecto.getPlazo()),
                proyecto.getEstado(),
                proyecto.getGobernanzaComunidad(),
                proyecto.getCupoMaximoTokens(),
                proyecto.getValorNominalToken(),
                proyecto.getMontoRecaudado(),
                proyecto.getRubro(),
                proyecto.getId()
            );
        }
        return findById(proyecto.getId()).orElseThrow();
    }

    @Override
    public Optional<Proyecto> findById(Long id) {
        try {
            Proyecto p = jdbc.queryForObject(
                "SELECT id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, " +
                "gobernanza_comunidad, cupo_maximo_tokens, valor_nominal_token, monto_recaudado, " +
                "rubro, es_destacado, fecha_boost, monto_boost, " +
                "created_at, updated_at, deleted_at FROM projects WHERE id = ? AND deleted_at IS NULL",
                rowMapper, id
            );
            return Optional.ofNullable(p);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Page<Proyecto> findAll(Pageable pageable) {
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE deleted_at IS NULL", Long.class
        );
        List<Proyecto> list = jdbc.query(
            "SELECT id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, " +
            "gobernanza_comunidad, cupo_maximo_tokens, valor_nominal_token, monto_recaudado, " +
            "rubro, es_destacado, fecha_boost, monto_boost, " +
            "created_at, updated_at, deleted_at FROM projects WHERE deleted_at IS NULL ORDER BY monto_boost DESC, created_at DESC LIMIT ? OFFSET ?",
            rowMapper, pageable.getPageSize(), pageable.getOffset()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Proyecto> findByCreadorId(Long creadorId, Pageable pageable) {
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE creador_id = ? AND deleted_at IS NULL",
            Long.class, creadorId
        );
        List<Proyecto> list = jdbc.query(
            "SELECT id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, " +
            "gobernanza_comunidad, cupo_maximo_tokens, valor_nominal_token, monto_recaudado, " +
            "rubro, es_destacado, fecha_boost, monto_boost, " +
            "created_at, updated_at, deleted_at FROM projects WHERE creador_id = ? AND deleted_at IS NULL ORDER BY monto_boost DESC, created_at DESC LIMIT ? OFFSET ?",
            rowMapper, creadorId, pageable.getPageSize(), pageable.getOffset()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Proyecto> findByFilters(List<String> estados, String search, Pageable pageable) {
        StringBuilder where = new StringBuilder("deleted_at IS NULL");
        List<Object> params = new ArrayList<>();

        if (estados != null && !estados.isEmpty()) {
            where.append(" AND estado IN (");
            for (int i = 0; i < estados.size(); i++) {
                where.append("?");
                if (i < estados.size() - 1) where.append(",");
                params.add(estados.get(i));
            }
            where.append(")");
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(titulo) LIKE LOWER(?) OR LOWER(descripcion) LIKE LOWER(?))");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
        }

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE " + where, Long.class, params.toArray()
        );

        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());

        List<Proyecto> list = jdbc.query(
            "SELECT id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, " +
            "gobernanza_comunidad, cupo_maximo_tokens, valor_nominal_token, monto_recaudado, " +
            "rubro, es_destacado, fecha_boost, monto_boost, " +
            "created_at, updated_at, deleted_at FROM projects WHERE " + where + " ORDER BY monto_boost DESC, created_at DESC LIMIT ? OFFSET ?",
            rowMapper, params.toArray()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Proyecto> findProjectsInFinancing() {
        return jdbc.query(
            "SELECT id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, " +
            "gobernanza_comunidad, cupo_maximo_tokens, valor_nominal_token, monto_recaudado, " +
            "rubro, es_destacado, fecha_boost, monto_boost, " +
            "created_at, updated_at, deleted_at FROM projects WHERE estado = 'FINANCIAMIENTO' AND deleted_at IS NULL",
            rowMapper
        );
    }

    private Timestamp toTimestamp(java.time.LocalDateTime ldt) {
        return ldt != null ? Timestamp.valueOf(ldt) : null;
    }
}
