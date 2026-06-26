package com.systeam.referrals.dto;

import java.math.BigDecimal;

public record ReferralStatsResponse(int referralCount, BigDecimal totalEarned) {}
