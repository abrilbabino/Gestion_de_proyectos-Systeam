package com.systeam.rewards.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RewardSummaryResponse {
    private BigDecimal total;
    private Map<String, BigDecimal> byType;
}
