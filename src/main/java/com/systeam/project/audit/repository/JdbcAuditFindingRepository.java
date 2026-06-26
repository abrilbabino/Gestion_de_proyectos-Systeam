package com.systeam.project.audit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditFindingRepository {

    private final JdbcTemplate jdbc;

    public JdbcAuditFindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<AuditFinding> rowMapper = (rs, rowNum) -> mapRow(rs);

    private AuditFinding mapRow(ResultSet rs) throws SQLException {
        AuditFinding f = new AuditFinding();
        f.setId(rs.getLong("id"));
        f.setProyectoId(rs.getLong("proyecto_id"));
        f.setAuditorId(rs.getLong("auditor_id"));
        f.setKybUrl(rs.getString("kyb_url"));
        f.setResultado(rs.getString("resultado"));
        f.setObservaciones(rs.getString("observaciones"));
        f.setRiskScore(rs.getString("risk_score"));
        f.setFinancialViabilityScore(rs.getObject("financial_viability_score", Integer.class));
        f.setTxHash(rs.getString("tx_hash"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            f.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        return f;
    }

    /**
     * Persists a new audit finding and returns it with its generated id.
     */
    public AuditFinding save(AuditFinding finding) {
        String sql = """
            INSERT INTO audit_findings
                (proyecto_id, auditor_id, kyb_url, resultado, observaciones, risk_score, financial_viability_score, tx_hash, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            RETURNING id, proyecto_id, auditor_id, kyb_url, resultado, observaciones, risk_score, financial_viability_score, tx_hash, created_at
            """;

        return jdbc.queryForObject(sql, rowMapper,
            finding.getProyectoId(),
            finding.getAuditorId(),
            finding.getKybUrl(),
            finding.getResultado(),
            finding.getObservaciones(),
            finding.getRiskScore(),
            finding.getFinancialViabilityScore(),
            finding.getTxHash()
        );
    }

    /**
     * Returns all findings for a project, ordered by creation time descending.
     */
    public List<AuditFinding> findByProyectoIdOrderByCreatedAtDesc(Long proyectoId) {
        return jdbc.query(
            "SELECT id, proyecto_id, auditor_id, kyb_url, resultado, observaciones, risk_score, financial_viability_score, tx_hash, created_at " +
            "FROM audit_findings WHERE proyecto_id = ? ORDER BY created_at DESC",
            rowMapper,
            proyectoId
        );
    }
}
