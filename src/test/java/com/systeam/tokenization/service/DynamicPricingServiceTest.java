package com.systeam.tokenization.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// verificar que la lógica de cálculo de precios dinámicos de tu aplicación funcione correctamente bajo diferentes escenarios
class DynamicPricingServiceTest {

    private DynamicPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new DynamicPricingService();
    }

    @Test
    void calcularPrecioDinamico_suministroTotalCeroOMenos_debeRetornarPrecioBase() {
        BigDecimal precioBase = new BigDecimal("10.00");
        BigDecimal factorVolatilidad = new BigDecimal("0.05");
        BigDecimal factorRendimiento = new BigDecimal("0.10");

        // Caso suministro == 0
        BigDecimal result1 = pricingService.calcularPrecioDinamico(precioBase, 0, 100, factorVolatilidad, factorRendimiento);
        assertThat(result1).isEqualTo(precioBase);

        // Caso suministro < 0
        BigDecimal result2 = pricingService.calcularPrecioDinamico(precioBase, -10, 100, factorVolatilidad, factorRendimiento);
        assertThat(result2).isEqualTo(precioBase);
    }

    @Test
    void calcularPrecioDinamico_conDemandaYFactores_debeCalcularCorrectamente() {
        BigDecimal precioBase = new BigDecimal("100.00");
        int suministroTotal = 1000;
        int cupoRestante = 200; // vendido = 800 (demandaRelativa = 0.8)
        BigDecimal factorVolatilidad = new BigDecimal("0.50"); // 1 + (0.8 * 0.5) = 1.40
        BigDecimal factorRendimiento = new BigDecimal("0.20"); // 1 + 0.20 = 1.20
        
        // precioEsperado = 100.00 * 1.40 * 1.20 = 168.00
        BigDecimal result = pricingService.calcularPrecioDinamico(
                precioBase, suministroTotal, cupoRestante, factorVolatilidad, factorRendimiento
        );

        assertThat(result).isEqualByComparingTo("168.00");
    }

    @Test
    void calcularPrecioDinamico_sinVentas_debeCalcularConDemandaCero() {
        BigDecimal precioBase = new BigDecimal("50.00");
        int suministroTotal = 500;
        int cupoRestante = 500; // vendido = 0 (demandaRelativa = 0)
        BigDecimal factorVolatilidad = new BigDecimal("0.30"); // 1 + 0 = 1.0
        BigDecimal factorRendimiento = new BigDecimal("0.10"); // 1 + 0.1 = 1.10

        // precioEsperado = 50.00 * 1.0 * 1.1 = 55.00
        BigDecimal result = pricingService.calcularPrecioDinamico(
                precioBase, suministroTotal, cupoRestante, factorVolatilidad, factorRendimiento
        );

        assertThat(result).isEqualByComparingTo("55.00");
    }

    @Test
    void calcularPrecioFinanciamiento_porDebajoDel70porciento_debeRetornarPrecioBase() {
        BigDecimal precioBase = new BigDecimal("5.00");
        BigDecimal montoRequerido = new BigDecimal("10000");

        // 10% recaudado
        BigDecimal r1 = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("1000"), montoRequerido);
        assertThat(r1).isEqualByComparingTo("5.00");

        // 50% recaudado
        BigDecimal r2 = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("5000"), montoRequerido);
        assertThat(r2).isEqualByComparingTo("5.00");

        // 70% recaudado (exacto en el umbral)
        BigDecimal r3 = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("7000"), montoRequerido);
        assertThat(r3).isEqualByComparingTo("5.00");
    }

    @Test
    void calcularPrecioFinanciamiento_porEncimaDel70porciento_debeSubirProgresivamente() {
        BigDecimal precioBase = new BigDecimal("5.00");
        BigDecimal montoRequerido = new BigDecimal("10000");

        // 85% recaudado → exceso = 15/30 * 20% = 10% → precio = 5.00 * 1.10 = 5.50
        BigDecimal r1 = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("8500"), montoRequerido);
        assertThat(r1).isEqualByComparingTo("5.50");

        // 100% recaudado → exceso = 30/30 * 20% = 20% → precio = 5.00 * 1.20 = 6.00
        BigDecimal r2 = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("10000"), montoRequerido);
        assertThat(r2).isEqualByComparingTo("6.00");
    }

    @Test
    void calcularPrecioFinanciamiento_nuncaSuperaElMaximoDe20porciento() {
        BigDecimal precioBase = new BigDecimal("5.00");
        BigDecimal montoRequerido = new BigDecimal("10000");

        // 200% recaudado (hardCap superado) → precio debe ser 6.00 max
        BigDecimal r = pricingService.calcularPrecioFinanciamiento(precioBase, new BigDecimal("20000"), montoRequerido);
        assertThat(r).isEqualByComparingTo("6.00");
    }

    @Test
    void calcularPrecioFinanciamiento_conMontoRequeridoCero_debeRetornarPrecioBase() {
        BigDecimal r = pricingService.calcularPrecioFinanciamiento(new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(r).isEqualByComparingTo("5.00");
    }

    @Test
    void calcularPrecioDinamico_redondeoAConDosDecimales_medioHaciaArriba() {
        BigDecimal precioBase = new BigDecimal("10.005");
        int suministroTotal = 100;
        int cupoRestante = 50; // vendido = 50 (demandaRelativa = 0.5)
        BigDecimal factorVolatilidad = new BigDecimal("0.1111"); // 1 + 0.05555 = 1.05555
        BigDecimal factorRendimiento = new BigDecimal("0.05"); // 1 + 0.05 = 1.05

        // precio = 10.005 * 1.05555 * 1.05 = 11.088629925 -> redondeado a 2 decimales = 11.09
        BigDecimal result = pricingService.calcularPrecioDinamico(
                precioBase, suministroTotal, cupoRestante, factorVolatilidad, factorRendimiento
        );

        assertThat(result).isEqualByComparingTo("11.09");
    }
}
