package com.systeam.investment.scheduler;

import com.systeam.investment.service.InvestmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvestmentSchedulerTest {

    @Mock
    private InvestmentService investmentService;

    private InvestmentScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InvestmentScheduler(investmentService);
    }

    @Nested
    @DisplayName("evaluateExpiredProjects")
    class EvaluateExpiredProjects {

        @Test
        void llamaAlServicio() {
            scheduler.evaluateExpiredProjects();

            verify(investmentService).processExpiredProjects();
        }

        @Test
        void cuandoElServicioLanzaExcepcion_noPropaga() {
            doThrow(new RuntimeException("DB error")).when(investmentService).processExpiredProjects();

            scheduler.evaluateExpiredProjects();

            verify(investmentService).processExpiredProjects();
        }
    }
}
