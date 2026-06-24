package com.systeam.eventos.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.systeam.eventos.dto.AsistenciaResponse;
import com.systeam.eventos.dto.EventoResponse;

@Repository
public class EventosRepository {

    private final JdbcTemplate jdbc;
    private final EventoRowMapper eventoMapper = new EventoRowMapper();
    private final AsistenciaRowMapper asistenciaMapper = new AsistenciaRowMapper();

    public EventosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insert(String titulo, String descripcion, java.time.LocalDateTime fechaEvento,
                       java.math.BigDecimal rewardAmount, Long proyectoId, Long createdBy,
                       String cronograma) {
        jdbc.update(
                "INSERT INTO eventos (titulo, descripcion, fecha_evento, reward_amount, proyecto_id, created_by, cronograma) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                titulo, descripcion, Timestamp.valueOf(fechaEvento), rewardAmount, proyectoId, createdBy, cronograma);
        return jdbc.queryForObject("SELECT LASTVAL()", Long.class);
    }

    public EventoResponse findById(Long id) {
        List<EventoResponse> rows = jdbc.query(
                "SELECT * FROM eventos WHERE id = ?", eventoMapper, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<EventoResponse> findAll() {
        return jdbc.query(
                "SELECT * FROM eventos ORDER BY fecha_evento DESC", eventoMapper);
    }

    public void update(Long id, String titulo, String descripcion,
                       java.time.LocalDateTime fechaEvento, java.math.BigDecimal rewardAmount,
                       Long proyectoId, String cronograma) {
        jdbc.update(
                "UPDATE eventos SET titulo = ?, descripcion = ?, fecha_evento = ?, " +
                "reward_amount = ?, proyecto_id = ?, cronograma = ? WHERE id = ?",
                titulo, descripcion, Timestamp.valueOf(fechaEvento), rewardAmount, proyectoId, cronograma, id);
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM eventos WHERE id = ?", id);
    }

    /**
     * Inserts an attendance record idempotently.
     * Returns true if inserted, false if duplicate (ON CONFLICT DO NOTHING).
     */
    public boolean insertAsistencia(Long eventoId, Long userId) {
        int rows = jdbc.update(
                "INSERT INTO evento_asistencias (evento_id, user_id) VALUES (?, ?) " +
                "ON CONFLICT (evento_id, user_id) DO NOTHING",
                eventoId, userId);
        return rows > 0;
    }

    public List<AsistenciaResponse> findAsistenciasByEvento(Long eventoId) {
        return jdbc.query(
                "SELECT * FROM evento_asistencias WHERE evento_id = ? ORDER BY confirmed_at DESC",
                asistenciaMapper, eventoId);
    }

    public int countAsistencias(Long eventoId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento_asistencias WHERE evento_id = ?",
                Integer.class, eventoId);
        return count != null ? count : 0;
    }

    private static class EventoRowMapper implements RowMapper<EventoResponse> {
        @Override
        public EventoResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            EventoResponse r = new EventoResponse();
            r.setId(rs.getLong("id"));
            r.setTitulo(rs.getString("titulo"));
            r.setDescripcion(rs.getString("descripcion"));
            r.setFechaEvento(rs.getTimestamp("fecha_evento").toLocalDateTime());
            r.setRewardAmount(rs.getBigDecimal("reward_amount"));
            r.setProyectoId((Long) rs.getObject("proyecto_id"));
            r.setCreatedBy((Long) rs.getObject("created_by"));
            r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            r.setCronograma(rs.getString("cronograma"));
            return r;
        }
    }

    private static class AsistenciaRowMapper implements RowMapper<AsistenciaResponse> {
        @Override
        public AsistenciaResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            AsistenciaResponse r = new AsistenciaResponse();
            r.setId(rs.getLong("id"));
            r.setEventoId(rs.getLong("evento_id"));
            r.setUserId(rs.getLong("user_id"));
            r.setConfirmedAt(rs.getTimestamp("confirmed_at").toLocalDateTime());
            return r;
        }
    }
}
