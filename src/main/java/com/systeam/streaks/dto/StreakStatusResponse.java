package com.systeam.streaks.dto;

import java.time.LocalDate;

public record StreakStatusResponse(int currentStreak, int longestStreak, LocalDate lastCheckIn, boolean alreadyCheckedInToday, boolean rewardEarned) {}
