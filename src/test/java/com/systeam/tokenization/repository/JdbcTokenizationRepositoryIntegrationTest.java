package com.systeam.tokenization.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.testutil.TestDatabaseHelper;

class JdbcTokenizationRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private JdbcTokenizationRepository repository;

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
        repository = new JdbcTokenizationRepository(jdbc);
        jdbc.execute("DELETE FROM subtokens");
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'Creator1')");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, created_at, updated_at) VALUES (1, 1, 'Proj1', 'Desc', 10000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, created_at, updated_at) VALUES (2, 1, 'Proj2', 'Desc', 20000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
    }

    @Test
    void findById_shouldReturnTokenWithProjectJoin() {
        insertSubToken(1, 1, "TokenA", "TKA", 500, new BigDecimal("25.00"), "0xaddr1");

        Optional<Map<String, Object>> result = repository.findById(1L);

        assertThat(result).isPresent();
        assertThat(((Number) result.get().get("id")).longValue()).isEqualTo(1L);
        assertThat(((Number) result.get().get("proyecto_id")).longValue()).isEqualTo(1L);
        assertThat(result.get().get("proyectoTitulo")).isEqualTo("Proj1");
    }

    @Test
    void findById_shouldReturnEmptyForMissing() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findByProjectId_shouldReturnToken() {
        insertSubToken(1, 1, "TokenA", "TKA", 500, new BigDecimal("25.00"), "0xaddr1");

        Optional<Map<String, Object>> result = repository.findByProjectId(1L);

        assertThat(result).isPresent();
        assertThat(((Number) result.get().get("proyecto_id")).longValue()).isEqualTo(1L);
    }

    @Test
    void findByProjectId_shouldReturnEmptyForMissing() {
        assertThat(repository.findByProjectId(999L)).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllTokens() {
        insertSubToken(1, 1, "TokenA", "TKA", 500, new BigDecimal("25.00"), "0xaddr1");
        insertSubToken(2, 2, "TokenB", "TKB", 300, new BigDecimal("15.00"), "0xaddr2");

        List<Map<String, Object>> list = repository.findAll();

        assertThat(list).hasSize(2);
    }

    @Test
    void findAll_shouldReturnEmptyList() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findAllPaged_shouldReturnPage() {
        insertSubToken(1, 1, "TokenA", "TKA", 500, new BigDecimal("25.00"), "0xaddr1");
        insertSubToken(2, 2, "TokenB", "TKB", 300, new BigDecimal("15.00"), "0xaddr2");

        Page<Map<String, Object>> page = repository.findAll(PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    private void insertSubToken(long id, long proyectoId, String nombre, String simbolo,
                                 int suministro, BigDecimal precio, String contractAddress) {
        jdbc.update("""
            INSERT INTO subtokens (id, proyecto_id, nombre, simbolo, suministro_total, cupo_restante, precio_actual, precio_base, factor_volatilidad, contract_address, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1.0, ?, NOW(), NOW())
            """, id, proyectoId, nombre, simbolo, suministro, suministro, precio, precio, contractAddress);
    }
}
