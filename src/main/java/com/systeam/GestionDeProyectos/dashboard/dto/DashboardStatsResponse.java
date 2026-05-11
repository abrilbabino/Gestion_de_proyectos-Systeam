package com.systeam.GestionDeProyectos.dashboard.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalUsers;
    private long totalInvestors;
    private long totalProjects;
    private long activeProjects;
    private Map<String, Long> projectsByStatus;
    private BigDecimal totalRequiredAmount;
}
