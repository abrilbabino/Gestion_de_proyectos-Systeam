package com.systeam.GestionDeProyectos.dashboard.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.systeam.GestionDeProyectos.dashboard.dto.DashboardStatsResponse;
import com.systeam.GestionDeProyectos.dashboard.repository.DashboardRepository;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public DashboardStatsResponse getStats() {
        long totalUsers = dashboardRepository.countUsers();
        long totalInvestors = dashboardRepository.countInvestors();
        long totalProjects = dashboardRepository.countProjects();
        long activeProjects = dashboardRepository.countActiveProjects();

        List<Object[]> statusRows = dashboardRepository.countProjectsByStatus();
        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            projectsByStatus.put((String) row[0], (Long) row[1]);
        }

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalInvestors(totalInvestors)
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .projectsByStatus(projectsByStatus)
                .totalRequiredAmount(dashboardRepository.sumRequiredAmount())
                .build();
    }
}
