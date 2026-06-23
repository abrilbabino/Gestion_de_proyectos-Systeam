package com.systeam.investment.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.systeam.shared.model.Inversion;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

@Repository
public class JdbcInvestmentRepository implements InvestmentRepository {

    private final JdbcTemplate jdbc;

    public JdbcInvestmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Inversion> rowMapper = (rs, rowNum) -> {
        Inversion inv = new Inversion();

        inv.setId(rs.getLong("id"));

        Usuario usuario = new Usuario();
        usuario.setId(rs.getLong("usuario_id"));
        inv.setUsuario(usuario);

        Proyecto proyecto = new Proyecto();
        proyecto.setId(rs.getLong("proyecto_id"));
        inv.setProyecto(proyecto);

        inv.setMontoIdea(rs.getBigDecimal("monto_idea"));
        inv.setSubTokensRecibidos(rs.getInt("sub_tokens_recibidos"));
        inv.setTxHash(rs.getString("tx_hash"));
        inv.setEstado(rs.getString("estado"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) inv.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) inv.setUpdatedAt(updatedAt.toLocalDateTime());

        // Manejar descuento_porcentaje que puede ser null
        Object desc = rs.getObject("descuento_porcentaje");
        if (desc != null) {
            inv.setDescuentoPorcentaje(((Number) desc).intValue());
        }

        return inv;
    };

    @Override
    public Inversion save(Inversion inversion) {
        if (inversion.getId() == null) {
            String sql = """
                INSERT INTO investments (usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos,
                    tx_hash, estado, descuento_porcentaje, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                RETURNING id
                """;
            Long id = jdbc.queryForObject(sql, Long.class,
                inversion.getUsuario().getId(),
                inversion.getProyecto().getId(),
                inversion.getMontoIdea(),
                inversion.getSubTokensRecibidos(),
                inversion.getTxHash(),
                inversion.getEstado(),
                inversion.getDescuentoPorcentaje()
            );
            inversion.setId(id);
        } else {
            String sql = """
                UPDATE investments SET monto_idea=?, sub_tokens_recibidos=?, tx_hash=?,
                    estado=?, descuento_porcentaje=?, updated_at=NOW()
                WHERE id=?
                """;
            jdbc.update(sql,
                inversion.getMontoIdea(),
                inversion.getSubTokensRecibidos(),
                inversion.getTxHash(),
                inversion.getEstado(),
                inversion.getDescuentoPorcentaje(),
                inversion.getId()
            );
        }
        return findById(inversion.getId()).orElseThrow();
    }

    @Override
    public Optional<Inversion> findById(Long id) {
        try {
            Inversion inv = jdbc.queryForObject("SELECT id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, tx_hash, estado, descuento_porcentaje, created_at, updated_at FROM investments WHERE id = ?", rowMapper, id);
            return Optional.ofNullable(inv);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Page<Inversion> findByUsuarioId(Long usuarioId, Pageable pageable) {
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM investments WHERE usuario_id = ?", Long.class, usuarioId
        );
        List<Inversion> list = jdbc.query(
            "SELECT id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, tx_hash, estado, descuento_porcentaje, created_at, updated_at FROM investments WHERE usuario_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper, usuarioId, pageable.getPageSize(), pageable.getOffset()
        );
        return new PageImpl<>(list, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Inversion> findByProyectoIdAndEstado(Long proyectoId, String estado) {
        return jdbc.query(
            "SELECT id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, tx_hash, estado, descuento_porcentaje, created_at, updated_at FROM investments WHERE proyecto_id = ? AND estado = ?",
            rowMapper, proyectoId, estado
        );
    }

    @Override
    public List<Inversion> findPendingRefundsByProyectoId(Long proyectoId) {
        return jdbc.query(
            "SELECT id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, tx_hash, estado, descuento_porcentaje, created_at, updated_at FROM investments WHERE proyecto_id = ? AND estado IN ('PENDIENTE', 'CONFIRMADA')",
            rowMapper, proyectoId
        );
    }
}
