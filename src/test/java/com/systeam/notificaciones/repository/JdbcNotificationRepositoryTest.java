package com.systeam.notificaciones.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.systeam.notificaciones.Notification;

@JdbcTest(properties = "spring.flyway.enabled=false")
@Sql("/schema-it.sql")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class JdbcNotificationRepositoryTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private JdbcNotificationRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcNotificationRepository(jdbc);
        jdbc.execute("DELETE FROM notificaciones");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'User1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'User2')");
    }

    @Test
    void findByUserId_retornaSoloDelUsuario() {
        insertNotification(1L, "TYPE_A", "Title A", "Msg A", "{}",
            LocalDateTime.now().minusMinutes(10), null);
        insertNotification(2L, "TYPE_B", "Title B", "Msg B", "{}",
            LocalDateTime.now().minusMinutes(5), null);
        insertNotification(1L, "TYPE_C", "Title C", "Msg C", "{}",
            LocalDateTime.now(), null);

        Page<Notification> page = repository.findByUserId(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByUserId_ordenDescPorCreatedAt() {
        insertNotification(1L, "OLD", "Old", "Old", "{}",
            LocalDateTime.now().minusHours(2), null);
        insertNotification(1L, "MID", "Mid", "Mid", "{}",
            LocalDateTime.now().minusHours(1), null);
        insertNotification(1L, "NEW", "New", "New", "{}",
            LocalDateTime.now(), null);

        Page<Notification> page = repository.findByUserId(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent().get(0).getType()).isEqualTo("NEW");
        assertThat(page.getContent().get(1).getType()).isEqualTo("MID");
        assertThat(page.getContent().get(2).getType()).isEqualTo("OLD");
    }

    @Test
    void findByUserId_paginacion() {
        for (int i = 0; i < 5; i++) {
            insertNotification(1L, "T" + i, "T", "M", "{}",
                LocalDateTime.now().minusMinutes(5 - i), null);
        }

        Page<Notification> first = repository.findByUserId(1L, PageRequest.of(0, 2));
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(5);

        Page<Notification> second = repository.findByUserId(1L, PageRequest.of(1, 2));
        assertThat(second.getContent()).hasSize(2);
        assertThat(second.getTotalElements()).isEqualTo(5);
    }

    @Test
    void markAsRead_actualizaReadAt() {
        insertNotification(1L, "TEST", "Title", "Msg", "{}", LocalDateTime.now(), null);

        Page<Notification> before = repository.findByUserId(1L, PageRequest.of(0, 10));
        Long id = before.getContent().get(0).getId();
        assertThat(before.getContent().get(0).getReadAt()).isNull();

        repository.markAsRead(id, 1L);

        Optional<Notification> updated = repository.findByIdAndUserId(id, 1L);
        assertThat(updated).isPresent();
        assertThat(updated.get().getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_propio_idempotente() {
        insertNotification(1L, "TEST", "Title", "Msg", "{}", LocalDateTime.now(), null);

        Page<Notification> before = repository.findByUserId(1L, PageRequest.of(0, 10));
        Long id = before.getContent().get(0).getId();

        // First mark
        repository.markAsRead(id, 1L);
        LocalDateTime firstReadAt = repository.findByIdAndUserId(id, 1L)
            .get().getReadAt();

        // Second mark — idempotent
        repository.markAsRead(id, 1L);
        LocalDateTime secondReadAt = repository.findByIdAndUserId(id, 1L)
            .get().getReadAt();

        assertThat(secondReadAt).isEqualTo(firstReadAt);
    }

    @Test
    void markAsRead_conUserIdIncorrecto_noActualiza() {
        insertNotification(1L, "TEST", "Title", "Msg", "{}", LocalDateTime.now(), null);

        Page<Notification> before = repository.findByUserId(1L, PageRequest.of(0, 10));
        Long id = before.getContent().get(0).getId();

        // Try to mark as read with wrong user
        repository.markAsRead(id, 999L);

        // Notification should still be unread
        Optional<Notification> notif = repository.findByIdAndUserId(id, 1L);
        assertThat(notif).isPresent();
        assertThat(notif.get().getReadAt()).isNull();
    }

    @Test
    void countUnread_exacto() {
        insertNotification(1L, "A", "A", "A", "{}", LocalDateTime.now(), null);
        insertNotification(1L, "B", "B", "B", "{}", LocalDateTime.now(), null);
        insertNotification(2L, "C", "C", "C", "{}", LocalDateTime.now(), null);

        // Mark one as read
        Page<Notification> page = repository.findByUserId(1L, PageRequest.of(0, 10));
        repository.markAsRead(page.getContent().get(0).getId(), 1L);

        assertThat(repository.countUnread(1L)).isEqualTo(1);
        assertThat(repository.countUnread(2L)).isEqualTo(1);
        assertThat(repository.countUnread(999L)).isEqualTo(0);
    }

    @Test
    void findByIdAndUserId_cuandoNoExiste_retornaEmpty() {
        Optional<Notification> result = repository.findByIdAndUserId(999L, 1L);
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserId_cuandoPerteneceAOtro_retornaEmpty() {
        insertNotification(1L, "TEST", "Title", "Msg", "{}", LocalDateTime.now(), null);
        Page<Notification> page = repository.findByUserId(1L, PageRequest.of(0, 10));
        Long id = page.getContent().get(0).getId();

        Optional<Notification> result = repository.findByIdAndUserId(id, 2L);
        assertThat(result).isEmpty();
    }

    private void insertNotification(Long userId, String type, String title,
                                     String message, String payload,
                                     LocalDateTime createdAt, LocalDateTime readAt) {
        String sql = "INSERT INTO notificaciones (recipient_user_id, type, title, message, payload, created_at, read_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, userId, type, title, message, payload, createdAt, readAt);
    }
}
