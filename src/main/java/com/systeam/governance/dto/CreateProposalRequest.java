package com.systeam.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateProposalRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private Integer proposalType;

    private byte[] data;

    private Long projectId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getProposalType() { return proposalType; }
    public void setProposalType(Integer proposalType) { this.proposalType = proposalType; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}
