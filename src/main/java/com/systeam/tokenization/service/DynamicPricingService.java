package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingService.class);

    public BigDecimal calcularPrecioDinamico(
            BigDecimal precioBase,
            int suministroTotal,
            int cupoRestante,
            BigDecimal factorVolatilidad
    ) {
        if (suministroTotal <= 0) {
            return precioBase;
        }

        BigDecimal vendido = BigDecimal.valueOf(suministroTotal - cupoRestante);
        BigDecimal total = BigDecimal.valueOf(suministroTotal);
        BigDecimal demandaRelativa = vendido.divide(total, 4, RoundingMode.HALF_UP);

        BigDecimal incremento = BigDecimal.ONE.add(
            demandaRelativa.multiply(factorVolatilidad)
        );

        BigDecimal precioDinamico = precioBase.multiply(incremento)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Precio dinamico: base={}, vendido={}/{}, demandaRelativa={}, precio={}",
                precioBase, vendido, total, demandaRelativa, precioDinamico);

        return precioDinamico;
    }
}
