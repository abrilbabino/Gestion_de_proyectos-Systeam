package com.systeam.project.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.systeam.project.service.BoostService;

@Component
public class BoostScheduler {

    private static final Logger log = LoggerFactory.getLogger(BoostScheduler.class);
    private final BoostService boostService;

    public BoostScheduler(BoostService boostService) {
        this.boostService = boostService;
    }

    // Ejecutar todos los días a la medianoche (00:00:00)
    @Scheduled(cron = "0 0 0 * * ?")
    public void applyDecay() {
        log.info("Iniciando aplicación de decaimiento temporal a los proyectos destacados...");
        try {
            int afectados = boostService.applyDecayToBoosts();
            log.info("Decaimiento completado. Proyectos afectados: {}", afectados);
        } catch (Exception e) {
            log.error("Error al aplicar decaimiento a destacados", e);
        }
    }
}
