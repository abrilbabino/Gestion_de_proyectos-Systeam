package com.systeam.streaks.service;

import com.systeam.rewards.service.RewardService;
import com.systeam.streaks.dto.StreakStatusResponse;
import com.systeam.streaks.model.StreakStatus;
import com.systeam.streaks.repository.StreakRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class StreakService {

    static final BigDecimal STREAK_REWARD = new BigDecimal("10");

    private final StreakRepository streakRepository;
    private final RewardService rewardService;

    public StreakService(StreakRepository streakRepository, RewardService rewardService) {
        this.streakRepository = streakRepository;
        this.rewardService = rewardService;
    }

    @Transactional
    public StreakStatusResponse checkIn(Long userId) {
        StreakStatus status = streakRepository.checkIn(userId);
        boolean rewardEarned = false;
        if (!status.alreadyCheckedInToday()) {
            LocalDate today = LocalDate.now();
            long dateRefId = today.getYear() * 10000L
                + today.getMonthValue() * 100L
                + today.getDayOfMonth();
            rewardService.accrue(userId, "DAILY_STREAK", "streak", dateRefId, null, STREAK_REWARD);
            rewardEarned = true;
        }
        return toResponse(status, rewardEarned);
    }

    public StreakStatusResponse getStatus(Long userId) {
        StreakStatus status = streakRepository.findByUser(userId)
            .orElse(new StreakStatus(userId, 0, 0, null, false));
        return toResponse(status, false);
    }

    private StreakStatusResponse toResponse(StreakStatus status, boolean rewardEarned) {
        return new StreakStatusResponse(
            status.currentStreak(),
            status.longestStreak(),
            status.lastCheckIn(),
            status.alreadyCheckedInToday(),
            rewardEarned
        );
    }
}
