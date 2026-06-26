package com.systeam.streaks.repository;

import com.systeam.streaks.model.StreakStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class StreakRepository {

    private final JdbcTemplate jdbc;

    public StreakRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StreakStatus checkIn(Long userId) {
        LocalDate today = LocalDate.now();

        // Single atomic UPSERT — eliminates TOCTOU between SELECT and INSERT/UPDATE
        List<Map<String, Object>> rows = jdbc.queryForList("""
            INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_check_in, updated_at)
            VALUES (?, 1, 1, CURRENT_DATE, now())
            ON CONFLICT (user_id) DO UPDATE SET
              current_streak = CASE
                WHEN user_streaks.last_check_in = CURRENT_DATE         THEN user_streaks.current_streak
                WHEN user_streaks.last_check_in = CURRENT_DATE - 1     THEN user_streaks.current_streak + 1
                ELSE 1
              END,
              longest_streak = GREATEST(
                user_streaks.longest_streak,
                CASE
                  WHEN user_streaks.last_check_in = CURRENT_DATE         THEN user_streaks.current_streak
                  WHEN user_streaks.last_check_in = CURRENT_DATE - 1     THEN user_streaks.current_streak + 1
                  ELSE 1
                END
              ),
              last_check_in = CASE
                WHEN user_streaks.last_check_in = CURRENT_DATE THEN user_streaks.last_check_in
                ELSE CURRENT_DATE
              END,
              updated_at = now()
            RETURNING current_streak, longest_streak, last_check_in
            """, userId);

        Map<String, Object> row = rows.get(0);
        LocalDate lastCheckIn = ((Date) row.get("last_check_in")).toLocalDate();
        int currentStreak = ((Number) row.get("current_streak")).intValue();
        int longestStreak = ((Number) row.get("longest_streak")).intValue();

        // alreadyCheckedInToday is intentionally false: accrue() uses the reward_ledger
        // ON CONFLICT as its own idempotency guard, so double-calling is safe.
        return new StreakStatus(userId, currentStreak, longestStreak, lastCheckIn, false);
    }

    public Optional<StreakStatus> findByUser(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT current_streak, longest_streak, last_check_in FROM user_streaks WHERE user_id = ?", userId
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        LocalDate lastCheckIn = row.get("last_check_in") != null
            ? ((Date) row.get("last_check_in")).toLocalDate()
            : null;
        int currentStreak = ((Number) row.get("current_streak")).intValue();
        int longestStreak = ((Number) row.get("longest_streak")).intValue();
        boolean alreadyToday = LocalDate.now().equals(lastCheckIn);
        return Optional.of(new StreakStatus(userId, currentStreak, longestStreak, lastCheckIn, alreadyToday));
    }
}
