package com.systeam.project.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.testutil.TestDatabaseHelper;

class JdbcAuditFindingRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private JdbcAuditFindingRepository repository;

    @BeforeAll
    static void init() {
        ds = TestDatabaseHelper.createDataSource();
        jdbc = new JdbcTemplate(ds);
        TestDatabaseHelper.executeSchema(jdbc);
    }

    @AfterAll
    static void shutdown() {
        TestDatabaseHelper.closeDataSource(ds);
    }

    @BeforeEach
    void setUp() {
        repository = new JdbcAuditFindingRepository(jdbc);
        jdbc.execute("DELETE FROM audit_findings");
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'Auditor1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'Auditor2')");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (1, 1, 'Proj1', 10000, NOW(), 'EN_AUDITORIA', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (2, 1, 'Proj2', 20000, NOW(), 'EN_AUDITORIA', NOW(), NOW())");
    }

    @Test
    void findByProyectoIdOrderByCreatedAtDesc_shouldReturnFindingsOrdered() {
        insertFinding(1, 1L, 1L, "https://kyb.example.com/a", "APROBADO", "Primero", null, OffsetDateTime.now().minusDays(2));
        insertFinding(2, 1L, 1L, "https://kyb.example.com/b", "RECHAZADO", "Segundo", null, OffsetDateTime.now().minusDays(1));
        insertFinding(3, 2L, 2L, "https://kyb.example.com/c", "APROBADO", "Tercero", null, OffsetDateTime.now());

        List<AuditFinding> findings = repository.findByProyectoIdOrderByCreatedAtDesc(1L);

        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).getObservaciones()).isEqualTo("Segundo");
        assertThat(findings.get(1).getObservaciones()).isEqualTo("Primero");
    }

    @Test
    void findByProyectoIdOrderByCreatedAtDesc_shouldReturnEmptyForMissingProject() {
        List<AuditFinding> findings = repository.findByProyectoIdOrderByCreatedAtDesc(999L);

        assertThat(findings).isEmpty();
    }

    @Test
    void findByProyectoIdOrderByCreatedAtDesc_shouldIncludeTxHash() {
        insertFinding(1, 1L, 1L, "https://kyb.example.com/d", "APROBADO", "Con tx", "0xdef456", OffsetDateTime.now());

        List<AuditFinding> findings = repository.findByProyectoIdOrderByCreatedAtDesc(1L);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getTxHash()).isEqualTo("0xdef456");
    }

    @Test
    void insertViaJdbc_shouldPersistFinding() {
        insertFinding(1, 1L, 1L, "https://kyb.example.com/e", "APROBADO", "Persist OK", null, OffsetDateTime.now());

        List<AuditFinding> findings = repository.findByProyectoIdOrderByCreatedAtDesc(1L);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getProyectoId()).isEqualTo(1L);
        assertThat(findings.get(0).getAuditorId()).isEqualTo(1L);
        assertThat(findings.get(0).getKybUrl()).isEqualTo("https://kyb.example.com/e");
        assertThat(findings.get(0).getResultado()).isEqualTo("APROBADO");
        assertThat(findings.get(0).getCreatedAt()).isNotNull();
    }

    private void insertFinding(long id, Long proyectoId, Long auditorId, String kybUrl, String resultado, String observaciones, String txHash, OffsetDateTime createdAt) {
        jdbc.update("""
            INSERT INTO audit_findings (id, proyecto_id, auditor_id, kyb_url, resultado, observaciones, tx_hash, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, proyectoId, auditorId, kybUrl, resultado, observaciones, txHash, createdAt);
    }
}
