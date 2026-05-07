package com.systeam.GestionDeProyectos.project.repository;

import java.sql.Timestamp;
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

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) p.setUpdatedAt(updatedAt.toLocalDateTime());

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) p.setDeletedAt(deletedAt.toLocalDateTime());

        return p;
    };

    @Override
    public Proyecto save(Proyecto proyecto) {
        if (proyecto.getId() == null) {
            String sql = """
                INSERT INTO projects (titulo, descripcion, monto_requerido, plazo, estado,
                    gobernanza_comunidad, creador_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                RETURNING id
                """;
            Long id = jdbc.queryForObject(sql, Long.class,
                proyecto.getTitulo(),
                proyecto.getDescripcion(),
                proyecto.getMontoRequerido(),
                toTimestamp(proyecto.getPlazo()),
                proyecto.getEstado(),
                proyecto.getGobernanzaComunidad(),
                proyecto.getCreador().getId()
            );
            proyecto.setId(id);
        } else {
            String sql = """
                UPDATE projects SET titulo=?, descripcion=?, monto_requerido=?, plazo=?,
                    estado=?, gobernanza_comunidad=?, updated_at=NOW()
                WHERE id=? AND deleted_at IS NULL
                """;
            jdbc.update(sql,
                proyecto.getTitulo(),
                proyecto.getDescripcion(),
                proyecto.getMontoRequerido(),
                toTimestamp(proyecto.getPlazo()),
                proyecto.getEstado(),
                proyecto.getGobernanzaComunidad(),
                proyecto.getId()
            );
        }
        return findById(proyecto.getId()).orElseThrow();
    }

    @Override
    public Optional<Proyecto> findById(Long id) {
        try {
            Proyecto p = jdbc.queryForObject(
                "SELECT * FROM projects WHERE id = ? AND deleted_at IS NULL",
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
            "SELECT * FROM projects WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT ? OFFSET ?",
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
            "SELECT * FROM projects WHERE creador_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper, creadorId, pageable.getPageSize(), pageable.getOffset()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Proyecto> findByFilters(String estado, String search, Pageable pageable) {
        StringBuilder where = new StringBuilder("deleted_at IS NULL");
        List<Object> params = new ArrayList<>();

        if (estado != null && !estado.isBlank()) {
            where.append(" AND estado = ?");
            params.add(estado);
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
            "SELECT * FROM projects WHERE " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper, params.toArray()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Proyecto> findProjectsInFinancing() {
        return jdbc.query(
            "SELECT * FROM projects WHERE estado = 'FINANCIAMIENTO' AND deleted_at IS NULL",
            rowMapper
        );
    }

    private Timestamp toTimestamp(java.time.LocalDateTime ldt) {
        return ldt != null ? Timestamp.valueOf(ldt) : null;
    }
}
