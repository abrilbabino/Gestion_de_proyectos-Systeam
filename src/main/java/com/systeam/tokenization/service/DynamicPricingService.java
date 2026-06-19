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
    private static final BigDecimal PRECIO_MINIMO_RATIO = new BigDecimal("1.00");

    private static final BigDecimal UMBRAL_FINANCIAMIENTO = new BigDecimal("0.70");
    private static final BigDecimal MAX_INCREMENTO_FINANCIAMIENTO = new BigDecimal("0.20");
    private static final BigDecimal CIEN = new BigDecimal("100");

    public BigDecimal calcularPrecioFinanciamiento(
            BigDecimal precioBase,
            BigDecimal montoRecaudado,
            BigDecimal montoRequerido
    ) {
        if (montoRequerido.compareTo(BigDecimal.ZERO) <= 0) {
            return precioBase;
        }

        BigDecimal progreso = montoRecaudado.divide(montoRequerido, 4, RoundingMode.HALF_UP);

        if (progreso.compareTo(UMBRAL_FINANCIAMIENTO) <= 0) {
            log.debug("Precio financiamiento: base={}, progreso={} (umbral 70% no superado)", precioBase, progreso);
            return precioBase;
        }

        BigDecimal exceso = progreso.subtract(UMBRAL_FINANCIAMIENTO);
        BigDecimal rango = BigDecimal.ONE.subtract(UMBRAL_FINANCIAMIENTO);
        BigDecimal factorIncremento = exceso.divide(rango, 4, RoundingMode.HALF_UP)
                .multiply(MAX_INCREMENTO_FINANCIAMIENTO);

        BigDecimal precioMaximo = precioBase.multiply(BigDecimal.ONE.add(MAX_INCREMENTO_FINANCIAMIENTO));
        BigDecimal precio = precioBase.multiply(BigDecimal.ONE.add(factorIncremento))
                .setScale(2, RoundingMode.HALF_UP);

        if (precio.compareTo(precioMaximo) > 0) {
            precio = precioMaximo;
        }

        log.debug("Precio financiamiento: base={}, progreso={}, factorIncremento={}, precio={}",
                precioBase, progreso, factorIncremento, precio);
        return precio;
    }

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
