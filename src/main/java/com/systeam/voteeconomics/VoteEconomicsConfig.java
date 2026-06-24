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
    private final BigDecimal investorDiscount;
    private final BigDecimal minVoteCost;

    /** Backwards-compatible constructor (no discount fields). */
    public VoteEconomicsConfig(BigDecimal voteCost, BigDecimal voteReward, Long treasuryUserId) {
        this(voteCost, voteReward, treasuryUserId, BigDecimal.ONE, BigDecimal.ONE);
    }
}
