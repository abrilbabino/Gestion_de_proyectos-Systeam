package com.systeam.governance.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;

public class ProposalResponse {

    private Long id;
    private BigInteger onChainId;
    private String proposerAddress;
    private Long proposerUserId;
    private String proposerName;
    private String title;
    private String description;
    private String proposalType;
    private String status;
    private BigInteger forVotes;
    private BigInteger againstVotes;
    private BigInteger totalVotes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime executedAt;
    private Long projectId;
    private String txHash;
    private LocalDateTime createdAt;

    public ProposalResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigInteger getOnChainId() { return onChainId; }
    public void setOnChainId(BigInteger onChainId) { this.onChainId = onChainId; }
    public String getProposerAddress() { return proposerAddress; }
    public void setProposerAddress(String proposerAddress) { this.proposerAddress = proposerAddress; }
    public Long getProposerUserId() { return proposerUserId; }
    public void setProposerUserId(Long proposerUserId) { this.proposerUserId = proposerUserId; }
    public String getProposerName() { return proposerName; }
    public void setProposerName(String proposerName) { this.proposerName = proposerName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProposalType() { return proposalType; }
    public void setProposalType(String proposalType) { this.proposalType = proposalType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigInteger getForVotes() { return forVotes; }
    public void setForVotes(BigInteger forVotes) { this.forVotes = forVotes; }
    public BigInteger getAgainstVotes() { return againstVotes; }
    public void setAgainstVotes(BigInteger againstVotes) { this.againstVotes = againstVotes; }
    public BigInteger getTotalVotes() { return totalVotes; }
    public void setTotalVotes(BigInteger totalVotes) { this.totalVotes = totalVotes; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
