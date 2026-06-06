package com.systeam.investment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.shared.model.Inversion;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.testutil.TestDatabaseHelper;

class JdbcInvestmentRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private JdbcInvestmentRepository repository;

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
        repository = new JdbcInvestmentRepository(jdbc);
        jdbc.execute("DELETE FROM investments");
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'User1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'User2')");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (1, 1, 'Proj1', 10000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (2, 1, 'Proj2', 20000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
    }

    @Test
    void save_shouldUpdateExisting() {
        insertInvestment(1, 1, 1, new BigDecimal("500"), 10, "PENDIENTE", null);

        Inversion inv = createInversion(1, 1, new BigDecimal("500"), 10, "PENDIENTE");
        inv.setId(1L);
        inv.setEstado("CONFIRMADA");
        inv.setTxHash("0xabc");
        Inversion updated = repository.save(inv);

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getEstado()).isEqualTo("CONFIRMADA");
        assertThat(updated.getTxHash()).isEqualTo("0xabc");
    }

    @Test
    void findById_shouldReturnInvestment() {
        insertInvestment(1, 1, 1, new BigDecimal("1000"), 20, "CONFIRMADA", "0x123");

        Inversion found = repository.findById(1L).orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getMontoIdea()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(found.getUsuario().getId()).isEqualTo(1L);
        assertThat(found.getProyecto().getId()).isEqualTo(1L);
    }

    @Test
    void findById_shouldReturnEmptyForMissing() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findByUsuarioId_shouldReturnUserInvestments() {
        insertInvestment(1, 1, 1, new BigDecimal("500"), 10, "CONFIRMADA", "0x1");
        insertInvestment(2, 1, 2, new BigDecimal("300"), 5, "PENDIENTE", null);
        insertInvestment(3, 2, 2, new BigDecimal("700"), 15, "CONFIRMADA", "0x3");

        Page<Inversion> page = repository.findByUsuarioId(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByProyectoIdAndEstado_shouldReturnMatching() {
        insertInvestment(1, 1, 1, new BigDecimal("500"), 10, "CONFIRMADA", "0x1");
        insertInvestment(2, 2, 1, new BigDecimal("300"), 5, "PENDIENTE", null);
        insertInvestment(3, 1, 2, new BigDecimal("700"), 15, "CONFIRMADA", "0x3");

        List<Inversion> list = repository.findByProyectoIdAndEstado(1L, "CONFIRMADA");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void findPendingRefundsByProyectoId_shouldReturnPendingAndConfirmed() {
        insertInvestment(1, 1, 1, new BigDecimal("500"), 10, "PENDIENTE", null);
        insertInvestment(2, 2, 1, new BigDecimal("300"), 5, "CONFIRMADA", "0x2");
        insertInvestment(3, 1, 1, new BigDecimal("200"), 3, "CANCELADA", "0x3");

        List<Inversion> list = repository.findPendingRefundsByProyectoId(1L);

        assertThat(list).hasSize(2);
    }

    private Inversion createInversion(long proyectoId, long usuarioId, BigDecimal monto, int tokens, String estado) {
        Inversion inv = new Inversion();
        Usuario u = new Usuario();
        u.setId(usuarioId);
        inv.setUsuario(u);
        Proyecto p = new Proyecto();
        p.setId(proyectoId);
        inv.setProyecto(p);
        inv.setMontoIdea(monto);
        inv.setSubTokensRecibidos(tokens);
        inv.setEstado(estado);
        return inv;
    }

    private void insertInvestment(long id, long usuarioId, long proyectoId, BigDecimal monto, int tokens, String estado, String txHash) {
        jdbc.update("""
            INSERT INTO investments (id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, tx_hash, estado, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, id, usuarioId, proyectoId, monto, tokens, txHash, estado);
    }
}
