package com.systeam.rewards.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RewardHistoryItem {
    private Long id;
    private String reason;
    private String refType;
    private Long refId;
    private String txHash;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
