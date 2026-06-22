package com.systeam.project.dto;

import jakarta.validation.constraints.NotNull;

public class ProjectVoteRequest {

    @NotNull
    private Boolean support;

    public Boolean getSupport() { return support; }
    public void setSupport(Boolean support) { this.support = support; }
}
