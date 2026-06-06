package com.systeam.notificaciones.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository {

    private final JdbcTemplate jdbc;

    public JdbcNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a notification row.
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
}
