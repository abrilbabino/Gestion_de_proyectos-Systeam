package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingService.class);

    private static final BigDecimal FACTOR_SOBRE_OFERTA = new BigDecimal("0.30");
    private static final BigDecimal PRECIO_MINIMO_RATIO = new BigDecimal("0.50");

    public BigDecimal calcularPrecioDinamico(
            BigDecimal precioBase,
            int suministroTotal,
            int cupoRestante,
            BigDecimal factorVolatilidad,
            BigDecimal factorRendimiento
    ) {
        return calcularPrecioDinamico(precioBase, suministroTotal, cupoRestante,
            factorVolatilidad, factorRendimiento, BigDecimal.ZERO);
    }

    public BigDecimal calcularPrecioDinamico(
            BigDecimal precioBase,
            int suministroTotal,
            int cupoRestante,
            BigDecimal factorVolatilidad,
            BigDecimal factorRendimiento,
            BigDecimal sobreOferta
    ) {
        if (suministroTotal <= 0) {
            return precioBase;
        }

        BigDecimal vendido = BigDecimal.valueOf(suministroTotal - cupoRestante);
        BigDecimal total = BigDecimal.valueOf(suministroTotal);
        BigDecimal demandaRelativa = vendido.divide(total, 4, RoundingMode.HALF_UP);

        BigDecimal componenteDemanda = demandaRelativa.multiply(factorVolatilidad);

        BigDecimal componenteSobreOferta = sobreOferta.multiply(FACTOR_SOBRE_OFERTA);

        BigDecimal ajuste = BigDecimal.ONE.add(componenteDemanda).subtract(componenteSobreOferta);
        if (ajuste.compareTo(BigDecimal.ZERO) < 0) {
            ajuste = BigDecimal.ZERO;
        }

        BigDecimal incrementoRendimiento = BigDecimal.ONE.add(factorRendimiento);

        BigDecimal precioDinamico = precioBase
                .multiply(ajuste)
                .multiply(incrementoRendimiento)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal precioMinimo = precioBase.multiply(PRECIO_MINIMO_RATIO);
        if (precioDinamico.compareTo(precioMinimo) < 0) {
            precioDinamico = precioMinimo;
        }

        log.debug("Precio dinamico: base={}, vendido={}/{}, demandaRelativa={}, " +
                "sobreOferta={}, factorRendimiento={}, precio={}",
                precioBase, vendido, total, demandaRelativa, sobreOferta, factorRendimiento, precioDinamico);

        return precioDinamico;
    }
}
