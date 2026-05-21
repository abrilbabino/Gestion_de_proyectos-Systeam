package com.systeam.investment.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.systeam.investment.service.InvestmentService;

@Component
@EnableScheduling
public class InvestmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvestmentScheduler.class);

    private final InvestmentService investmentService;

    public InvestmentScheduler(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @Scheduled(cron = "0 0 6 * * ?")
    public void evaluateExpiredProjects() {
        log.info("Iniciando evaluacion de proyectos vencidos...");
        try {
            investmentService.processExpiredProjects();
            log.info("Evaluacion de proyectos vencidos completada");
        } catch (Exception e) {
            log.error("Error al evaluar proyectos vencidos: {}", e.getMessage(), e);
        }
    }
}
