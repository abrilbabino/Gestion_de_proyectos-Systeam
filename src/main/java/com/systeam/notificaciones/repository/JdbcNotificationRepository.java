package com.systeam.notificaciones.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.systeam.notificaciones.Notification;

@Repository
public class JdbcNotificationRepository {

    private final JdbcTemplate jdbc;

    public JdbcNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a notification row without title/message (backward-compatible).
     *
     * @param recipientUserId the user that should receive the notification
     * @param type            notification type key (e.g. "PROJECT_REJECTED")
     * @param payloadJson     JSONB payload serialized as a JSON string
     */
    public void insert(Long recipientUserId, String type, String payloadJson) {
        jdbc.update(
            "INSERT INTO notificaciones (recipient_user_id, type, payload, created_at) VALUES (?, ?, ?::jsonb, NOW())",
            recipientUserId,
            type,
            payloadJson
        );
    }

    /**
     * Inserts a notification row with title and message.
     *
     * @param recipientUserId the user that should receive the notification
     * @param type            notification type key (e.g. "PROJECT_REJECTED")
     * @param title           human-readable title
     * @param message         human-readable message body
     * @param payloadJson     JSONB payload serialized as a JSON string
     */
    public void insert(Long recipientUserId, String type, String title, String message, String payloadJson) {
        jdbc.update(
            "INSERT INTO notificaciones (recipient_user_id, type, title, message, payload, created_at) VALUES (?, ?, ?, ?, ?::jsonb, NOW())",
            recipientUserId,
            type,
            title,
            message,
            payloadJson
        );
    }

    /**
     * Returns a paginated list of notifications for the given user,
     * ordered by {@code created_at DESC} (most recent first).
     */
    public Page<Notification> findByUserId(Long userId, Pageable pageable) {
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificaciones WHERE recipient_user_id = ?",
            Long.class,
            userId
        );

        List<Notification> items = jdbc.query(
            "SELECT id, recipient_user_id, type, payload, title, message, created_at, read_at " +
            "FROM notificaciones WHERE recipient_user_id = ? " +
            "ORDER BY created_at DESC LIMIT ? OFFSET ?",
            new NotificationRowMapper(),
            userId,
            pageable.getPageSize(),
            pageable.getOffset()
        );

        return new PageImpl<>(items, pageable, total);
    }

    /**
     * Finds a single notification by id + recipient. Returns empty if
     * the notification does not exist or belongs to another user.
     */
    public Optional<Notification> findByIdAndUserId(Long id, Long userId) {
        List<Notification> results = jdbc.query(
            "SELECT id, recipient_user_id, type, payload, title, message, created_at, read_at " +
            "FROM notificaciones WHERE id = ? AND recipient_user_id = ?",
            new NotificationRowMapper(),
            id,
            userId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Sets {@code read_at = NOW()} for a notification, but only if it
     * belongs to the given user. Idempotent — safe to call when already read.
     */
    public void markAsRead(Long id, Long userId) {
        jdbc.update(
            "UPDATE notificaciones SET read_at = NOW() WHERE id = ? AND recipient_user_id = ?",
            id,
            userId
        );
    }

    /**
     * Returns the number of notifications for the user where {@code read_at IS NULL}.
     */
    public long countUnread(Long userId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificaciones WHERE recipient_user_id = ? AND read_at IS NULL",
            Long.class,
            userId
        );
        return count != null ? count : 0L;
    }

    private static class NotificationRowMapper implements RowMapper<Notification> {
        @Override
        public Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp ca = rs.getTimestamp("created_at");
            Timestamp ra = rs.getTimestamp("read_at");
            return Notification.builder()
                .id(rs.getLong("id"))
                .recipientUserId(rs.getLong("recipient_user_id"))
                .type(rs.getString("type"))
                .payload(rs.getString("payload"))
                .title(rs.getString("title"))
                .message(rs.getString("message"))
                .createdAt(ca != null ? ca.toLocalDateTime() : null)
                .readAt(ra != null ? ra.toLocalDateTime() : null)
                .build();
        }
    }
}
