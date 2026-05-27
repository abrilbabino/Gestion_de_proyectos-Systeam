package com.systeam.governance.dto;

import jakarta.validation.constraints.NotNull;

public class VoteRequest {

    @NotNull
    private Long proposalId;

    @NotNull
    private Boolean support;

    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public Boolean getSupport() { return support; }
    public void setSupport(Boolean support) { this.support = support; }
}
