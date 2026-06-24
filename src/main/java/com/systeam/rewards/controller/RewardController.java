package com.systeam.rewards.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.rewards.dto.RewardHistoryItem;
import com.systeam.rewards.dto.RewardSummaryResponse;
import com.systeam.rewards.repository.RewardQueryRepository;
import com.systeam.security.JwtPrincipal;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

    private final RewardQueryRepository rewardQueryRepository;

    public RewardController(RewardQueryRepository rewardQueryRepository) {
        this.rewardQueryRepository = rewardQueryRepository;
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public List<RewardHistoryItem> history(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return rewardQueryRepository.findHistory(principal.userId(), page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public RewardSummaryResponse summary(@AuthenticationPrincipal JwtPrincipal principal) {
        Map<String, BigDecimal> summaryMap = rewardQueryRepository.findSummaryByUser(principal.userId());

        BigDecimal total = summaryMap.getOrDefault("total", BigDecimal.ZERO);
        summaryMap.remove("total");

        return RewardSummaryResponse.builder()
                .total(total)
                .byType(summaryMap)
                .build();
    }
}
