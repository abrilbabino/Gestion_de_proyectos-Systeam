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
