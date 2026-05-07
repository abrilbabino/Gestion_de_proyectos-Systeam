package com.systeam.GestionDeProyectos.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.systeam.GestionDeProyectos.project.model.ProjectStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String title;
    private String description;
    private String objective;
    private BigDecimal requiredAmount;
    private BigDecimal currentAmount;
    private ProjectStatus status;
    private Long creatorId;
    private Boolean gobernanzaComunidad;
    private Long cantidadDeTokens;
    private BigDecimal valorNominal;
    private String smartContractAddress;
    private LocalDateTime financingStartDate;
    private LocalDateTime financingEndDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal financingProgress;
    private SmartContractInfo smartContractInfo;

    @Data
    @Builder
    public static class SmartContractInfo {
        private String address;
        private Long totalSubTokens;
        private String distribution;
    }
}
