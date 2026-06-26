package com.systeam.streaks.model;

import java.time.LocalDate;

public record StreakStatus(Long userId, int currentStreak, int longestStreak, LocalDate lastCheckIn, boolean alreadyCheckedInToday) {}
