package com.systeam.notificaciones.event;

/**
 * Published after a governance proposal is created or its state changes.
 */
public class GovernanceProposalEvent {

    public enum Action {
        CREATED,
        EXECUTED,
        CANCELLED
    }

    private final Long proposalId;
    private final Long proposerId;
    private final Action action;

    public GovernanceProposalEvent(Long proposalId, Long proposerId, Action action) {
        this.proposalId  = proposalId;
        this.proposerId  = proposerId;
        this.action      = action;
    }

    public Long getProposalId()  { return proposalId; }
    public Long getProposerId()  { return proposerId; }
    public Action getAction()    { return action; }
}
