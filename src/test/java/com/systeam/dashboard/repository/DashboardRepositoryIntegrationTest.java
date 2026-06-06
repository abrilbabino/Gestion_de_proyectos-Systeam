package com.systeam.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.testutil.TestDatabaseHelper;

class DashboardRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private DashboardRepository repository;

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
        repository = new DashboardRepository(jdbc);
        jdbc.execute("DELETE FROM investments");
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM user_roles");
        jdbc.execute("DELETE FROM roles");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'User1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'User2')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (3, 'User3')");
        jdbc.execute("INSERT INTO roles (id, name) VALUES (1, 'INVESTOR')");
        jdbc.execute("INSERT INTO roles (id, name) VALUES (2, 'CREATOR')");
        jdbc.execute("INSERT INTO user_roles (user_id, role_id) VALUES (1, 1)");
        jdbc.execute("INSERT INTO user_roles (user_id, role_id) VALUES (2, 1)");
        jdbc.execute("INSERT INTO user_roles (user_id, role_id) VALUES (3, 2)");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (1, 1, 'Financing', 10000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (2, 2, 'Preparing', 20000, NOW(), 'PREPARACION', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (3, 1, 'Finished', 30000, NOW(), 'FINALIZADO', NOW(), NOW())");
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (4, 2, 'Cancelled', 40000, NOW(), 'CANCELADO', NOW(), NOW())");
    }

    @Test
    void countUsers_shouldReturnTotal() {
        assertThat(repository.countUsers()).isEqualTo(3);
    }

    @Test
    void countInvestors_shouldReturnDistinctCount() {
        assertThat(repository.countInvestors()).isEqualTo(2);
    }

    @Test
    void countProjects_shouldExcludeSoftDeleted() {
        jdbc.execute("UPDATE projects SET deleted_at = NOW() WHERE id = 4");
        assertThat(repository.countProjects()).isEqualTo(3);
    }

    @Test
    void countActiveProjects_shouldExcludeFinalizadoAndCancelado() {
        assertThat(repository.countActiveProjects()).isEqualTo(2);
    }

    @Test
    void countProjectsByStatus_shouldReturnGroupedCounts() {
        List<Object[]> stats = repository.countProjectsByStatus();

        assertThat(stats).hasSize(4);
        Map<String, Long> map = Map.of(
            (String) stats.get(0)[0], (Long) stats.get(0)[1],
            (String) stats.get(1)[0], (Long) stats.get(1)[1],
            (String) stats.get(2)[0], (Long) stats.get(2)[1],
            (String) stats.get(3)[0], (Long) stats.get(3)[1]
        );
        assertThat(map.get("FINANCIAMIENTO")).isEqualTo(1);
        assertThat(map.get("PREPARACION")).isEqualTo(1);
        assertThat(map.get("FINALIZADO")).isEqualTo(1);
        assertThat(map.get("CANCELADO")).isEqualTo(1);
    }

    @Test
    void sumRequiredAmount_shouldReturnSum() {
        BigDecimal sum = repository.sumRequiredAmount();
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    void sumRequiredAmount_shouldReturnZeroWhenNoProjects() {
        jdbc.execute("DELETE FROM projects");
        assertThat(repository.sumRequiredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getTopProjectsByInvestors_shouldReturnSorted() {
        jdbc.execute("INSERT INTO investments (id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, estado, created_at, updated_at) VALUES (1, 1, 1, 500, 10, 'CONFIRMADA', NOW(), NOW())");
        jdbc.execute("INSERT INTO investments (id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, estado, created_at, updated_at) VALUES (2, 2, 1, 300, 5, 'CONFIRMADA', NOW(), NOW())");
        jdbc.execute("INSERT INTO investments (id, usuario_id, proyecto_id, monto_idea, sub_tokens_recibidos, estado, created_at, updated_at) VALUES (3, 1, 2, 700, 15, 'CONFIRMADA', NOW(), NOW())");

        List<Map<String, Object>> top = repository.getTopProjectsByInvestors();

        assertThat(top).isNotEmpty();
        assertThat(top.get(0).get("name")).isEqualTo("Financing");
    }
}
