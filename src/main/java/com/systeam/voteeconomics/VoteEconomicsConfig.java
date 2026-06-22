package com.systeam.voteeconomics;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoteEconomicsConfig {

    private final BigDecimal voteCost;
    private final BigDecimal voteReward;
    private final Long treasuryUserId;
}
