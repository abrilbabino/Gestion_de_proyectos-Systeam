package com.systeam.notificaciones.event;

import java.math.BigDecimal;

/**
 * Published after a vote is cast and the economics (cost deduction + reward accrual)
 * are committed. Consumed by SSE broadcast and notification listeners.
 */
public class VoteRewardedEvent {

    private final Long userId;
    private final Long proposalId;
    private final BigDecimal cost;
    private final BigDecimal reward;
    private final String txHash;

    public VoteRewardedEvent(Long userId, Long proposalId, BigDecimal cost,
                             BigDecimal reward, String txHash) {
        this.userId = userId;
        this.proposalId = proposalId;
        this.cost = cost;
        this.reward = reward;
        this.txHash = txHash;
    }

    public Long getUserId()      { return userId; }
    public Long getProposalId()  { return proposalId; }
    public BigDecimal getCost()  { return cost; }
    public BigDecimal getReward(){ return reward; }
    public String getTxHash()    { return txHash; }
}
