package com.systeam.dashboard.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.systeam.dashboard.dto.DashboardStatsResponse;
import com.systeam.dashboard.repository.DashboardRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(dashboardRepository);
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        void retornaEstadisticasCompletas() {
            when(dashboardRepository.countUsers()).thenReturn(100L);
            when(dashboardRepository.countInvestors()).thenReturn(45L);
            when(dashboardRepository.countProjects()).thenReturn(30L);
            when(dashboardRepository.countActiveProjects()).thenReturn(20L);
            Map<String, Long> byStatus = new LinkedHashMap<>();
            byStatus.put("RECOLECTANDO", 10L);
            byStatus.put("EJECUCION", 8L);
            byStatus.put("FINALIZADO", 2L);
            when(dashboardRepository.countProjectsByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{"RECOLECTANDO", 10L},
                new Object[]{"EJECUCION", 8L},
                new Object[]{"FINALIZADO", 2L}
            ));
            when(dashboardRepository.sumRequiredAmount()).thenReturn(new BigDecimal("500000.00"));
            List<Map<String, Object>> top = List.of(
                Map.of("id", 1L, "name", "Proyecto A", "inversores", 25L)
            );
            when(dashboardRepository.getTopProjectsByInvestors()).thenReturn(top);

            DashboardStatsResponse result = service.getStats();

            assertThat(result.getTotalUsers()).isEqualTo(100L);
            assertThat(result.getTotalInvestors()).isEqualTo(45L);
            assertThat(result.getTotalProjects()).isEqualTo(30L);
            assertThat(result.getActiveProjects()).isEqualTo(20L);
            assertThat(result.getProjectsByStatus()).hasSize(3);
            assertThat(result.getProjectsByStatus().get("RECOLECTANDO")).isEqualTo(10L);
            assertThat(result.getTotalRequiredAmount()).isEqualByComparingTo("500000.00");
            assertThat(result.getTopProyectosInversores()).hasSize(1);
        }

        @Test
        void cuandoNoHayProyectos_todoCero() {
            when(dashboardRepository.countUsers()).thenReturn(0L);
            when(dashboardRepository.countInvestors()).thenReturn(0L);
            when(dashboardRepository.countProjects()).thenReturn(0L);
            when(dashboardRepository.countActiveProjects()).thenReturn(0L);
            when(dashboardRepository.countProjectsByStatus()).thenReturn(List.<Object[]>of());
            when(dashboardRepository.sumRequiredAmount()).thenReturn(BigDecimal.ZERO);
            when(dashboardRepository.getTopProjectsByInvestors()).thenReturn(List.of());

            DashboardStatsResponse result = service.getStats();

            assertThat(result.getTotalUsers()).isZero();
            assertThat(result.getTotalInvestors()).isZero();
            assertThat(result.getTotalProjects()).isZero();
            assertThat(result.getActiveProjects()).isZero();
            assertThat(result.getProjectsByStatus()).isEmpty();
            assertThat(result.getTotalRequiredAmount()).isEqualByComparingTo("0");
            assertThat(result.getTopProyectosInversores()).isEmpty();
        }

        @Test
        void projectsByStatus_conUnSoloEstado() {
            when(dashboardRepository.countUsers()).thenReturn(50L);
            when(dashboardRepository.countInvestors()).thenReturn(10L);
            when(dashboardRepository.countProjects()).thenReturn(5L);
            when(dashboardRepository.countActiveProjects()).thenReturn(3L);
            when(dashboardRepository.countProjectsByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{"RECOLECTANDO", 5L}
            ));
            when(dashboardRepository.sumRequiredAmount()).thenReturn(new BigDecimal("1000.00"));
            when(dashboardRepository.getTopProjectsByInvestors()).thenReturn(List.of());

            DashboardStatsResponse result = service.getStats();

            assertThat(result.getProjectsByStatus()).hasSize(1);
            assertThat(result.getProjectsByStatus().get("RECOLECTANDO")).isEqualTo(5L);
        }

        @Test
        void topProyectosConVarios() {
            when(dashboardRepository.countUsers()).thenReturn(200L);
            when(dashboardRepository.countInvestors()).thenReturn(80L);
            when(dashboardRepository.countProjects()).thenReturn(50L);
            when(dashboardRepository.countActiveProjects()).thenReturn(30L);
            when(dashboardRepository.countProjectsByStatus()).thenReturn(List.<Object[]>of());
            when(dashboardRepository.sumRequiredAmount()).thenReturn(BigDecimal.TEN);
            List<Map<String, Object>> top = List.of(
                Map.of("id", 1L, "name", "A", "inversores", 30L),
                Map.of("id", 2L, "name", "B", "inversores", 20L)
            );
            when(dashboardRepository.getTopProjectsByInvestors()).thenReturn(top);

            DashboardStatsResponse result = service.getStats();

            assertThat(result.getTopProyectosInversores()).hasSize(2);
            assertThat(result.getTopProyectosInversores().get(0).get("name")).isEqualTo("A");
        }
    }
}
