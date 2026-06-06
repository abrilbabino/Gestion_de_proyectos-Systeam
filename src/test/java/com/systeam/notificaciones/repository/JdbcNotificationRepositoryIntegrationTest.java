package com.systeam.notificaciones.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.testutil.TestDatabaseHelper;

class JdbcNotificationRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private JdbcNotificationRepository repository;

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
        repository = new JdbcNotificationRepository(jdbc);
        jdbc.execute("DELETE FROM notificaciones");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'User1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'User2')");
    }

    @Test
    void insert_shouldPersistNotification() {
        String payload = "{\"message\": \"Proyecto aprobado\", \"proyectoId\": 1}";

        repository.insert(1L, "PROJECT_APPROVED", payload);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM notificaciones WHERE recipient_user_id = 1"
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("PROJECT_APPROVED");
        assertThat(rows.get(0).get("payload")).isNotNull();
        assertThat(rows.get(0).get("created_at")).isNotNull();
        assertThat(rows.get(0).get("read_at")).isNull();
    }

    @Test
    void insert_shouldHandleMultipleNotifications() {
        repository.insert(1L, "PROJECT_APPROVED", "{\"id\": 1}");
        repository.insert(1L, "PROJECT_REJECTED", "{\"id\": 2, \"reason\": \"kyb\"}");
        repository.insert(2L, "INVESTMENT_CONFIRMED", "{\"amount\": 500}");

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM notificaciones WHERE recipient_user_id = 1 ORDER BY id"
        );
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("type")).isEqualTo("PROJECT_APPROVED");
        assertThat(rows.get(1).get("type")).isEqualTo("PROJECT_REJECTED");
    }

    @Test
    void insert_shouldHandleLargePayload() {
        String largePayload = "{\"data\": \"" + "x".repeat(1000) + "\"}";

        repository.insert(1L, "BULK_NOTIFICATION", largePayload);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM notificaciones WHERE recipient_user_id = 1"
        );
        assertThat(rows).hasSize(1);
        String savedPayload = (String) rows.get(0).get("payload");
        assertThat(savedPayload).contains("x".repeat(1000));
    }

    @Test
    void insert_shouldSetTimestamps() {
        repository.insert(1L, "TEST", "{}");

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM notificaciones WHERE recipient_user_id = 1"
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("created_at")).isNotNull();
        assertThat(rows.get(0).get("read_at")).isNull();
    }
}
