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
        LocalDate yesterday = today.minusDays(1);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT current_streak, longest_streak, last_check_in FROM user_streaks WHERE user_id = ?", userId
        );

        if (rows.isEmpty()) {
            jdbc.update(
                "INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_check_in, updated_at) VALUES (?, 1, 1, ?, now())",
                userId, Date.valueOf(today)
            );
            return new StreakStatus(userId, 1, 1, today, false);
        }

        Map<String, Object> row = rows.get(0);
        LocalDate lastCheckIn = row.get("last_check_in") != null
            ? ((Date) row.get("last_check_in")).toLocalDate()
            : null;
        int currentStreak = ((Number) row.get("current_streak")).intValue();
        int longestStreak = ((Number) row.get("longest_streak")).intValue();

        if (today.equals(lastCheckIn)) {
            return new StreakStatus(userId, currentStreak, longestStreak, lastCheckIn, true);
        }

        int newStreak = yesterday.equals(lastCheckIn) ? currentStreak + 1 : 1;
        int newLongest = Math.max(longestStreak, newStreak);

        jdbc.update(
            "UPDATE user_streaks SET current_streak = ?, longest_streak = ?, last_check_in = ?, updated_at = now() WHERE user_id = ?",
            newStreak, newLongest, Date.valueOf(today), userId
        );

        return new StreakStatus(userId, newStreak, newLongest, today, false);
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
