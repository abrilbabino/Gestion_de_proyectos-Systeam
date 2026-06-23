package com.systeam.project.dto;

import jakarta.validation.constraints.NotNull;

public class ProjectVoteRequest {

    @NotNull
    private Boolean support;

    @NotNull
    private String txHash;

    public Boolean getSupport() { return support; }
    public void setSupport(Boolean support) { this.support = support; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
}
