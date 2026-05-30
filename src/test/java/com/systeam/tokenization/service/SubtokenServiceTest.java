package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.systeam.tokenization.dto.SubtokenPriceResponse;

@ExtendWith(MockitoExtension.class)
class SubtokenServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private final DynamicPricingService pricingService = new DynamicPricingService();

    private SubtokenService subtokenService;

    private final Long proyectoId = 1L;
    private final Long subtokenId = 10L;
    private final Long usuarioId = 100L;

    @BeforeEach
    void setUp() {
        subtokenService = new SubtokenService(jdbc, pricingService);
    }

    @Test
    void findSubtokenByProject_cuandoExiste_debeRetornarMapa() {
        // Arrange
        Map<String, Object> mockSubtoken = new HashMap<>();
        mockSubtoken.put("id", subtokenId);
        mockSubtoken.put("precio_base", new BigDecimal("1.00"));
        mockSubtoken.put("suministro_total", 1000);
        mockSubtoken.put("cupo_restante", 400);
        mockSubtoken.put("factor_volatilidad", new BigDecimal("0.10"));
        mockSubtoken.put("precio_actual", new BigDecimal("1.10"));
        mockSubtoken.put("contract_address", "0xABC");

        when(jdbc.query(
            contains("FROM subtokens"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockSubtoken));

        // Act
        Map<String, Object> result = subtokenService.findSubtokenByProject(proyectoId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(subtokenId);
    }

    @Test
    void findSubtokenByProject_cuandoNoExiste_debeRetornarNull() {
        // Arrange
        when(jdbc.query(
            contains("FROM subtokens"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> result = subtokenService.findSubtokenByProject(proyectoId);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void calcularPrecio_debeCalcularCorrectamenteUsandoDynamicPricingService() {
        // Arrange
        BigDecimal precioBase = new BigDecimal("1.50");
        int suministroTotal = 1000;
        int cupoRestante = 400; // vendido = 600 -> demandaRelativa = 0.60
        BigDecimal factorVolatilidad = new BigDecimal("0.10"); // demandaIncremento = 1 + (0.60 * 0.10) = 1.06

        // Mock obtenerFactorRendimiento
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "EJECUCION");
        mockProject.put("monto_requerido", new BigDecimal("10000.00"));
        mockProject.put("monto_recaudado", new BigDecimal("5000.00"));

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject)); // EJECUCION returns factorRendimiento = 0.50

        // precio esperado = 1.50 * 1.06 * 1.50 = 2.385 -> redondeado = 2.39

        // Act
        BigDecimal result = subtokenService.calcularPrecio(
            precioBase, suministroTotal, cupoRestante, factorVolatilidad, proyectoId
        );

        // Assert
        assertThat(result).isEqualByComparingTo("2.39");
    }

    @Test
    void obtenerFactorRendimiento_cuandoNoExisteProyecto_debeRetornarCero() {
        // Arrange
        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(Collections.emptyList());

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void obtenerFactorRendimiento_preparacion_debeRetornarCero() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "PREPARACION");

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void obtenerFactorRendimiento_ejecucion_debeRetornarCincuentaPorciento() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "EJECUCION");

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.50");
    }

    @Test
    void obtenerFactorRendimiento_finalizado_debeRetornarSetentaycincoPorciento() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "FINALIZADO");

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.75");
    }

    @Test
    void obtenerFactorRendimiento_financiamiento_conMontoRequeridoCero_debeRetornarQuincePorciento() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "FINANCIAMIENTO");
        mockProject.put("monto_requerido", BigDecimal.ZERO);
        mockProject.put("monto_recaudado", BigDecimal.ZERO);

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.15");
    }

    @Test
    void obtenerFactorRendimiento_financiamiento_conProgresoCincoPorciento() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "FINANCIAMIENTO");
        mockProject.put("monto_requerido", new BigDecimal("1000.00"));
        mockProject.put("monto_recaudado", new BigDecimal("500.00"));

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.25");
    }

    @Test
    void obtenerFactorRendimiento_financiamiento_progresoMaximoCapTreintaycincoPorciento() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "FINANCIAMIENTO");
        mockProject.put("monto_requerido", new BigDecimal("1000.00"));
        mockProject.put("monto_recaudado", new BigDecimal("1200.00"));

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0.35");
    }

    @Test
    void obtenerFactorRendimiento_estadoDesconocido_debeRetornarCero() {
        // Arrange
        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "DESCONOCIDO");

        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        BigDecimal result = subtokenService.obtenerFactorRendimiento(proyectoId);

        // Assert
        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void obtenerPrecioConDetalle_cuandoNoExisteSubtoken_debeRetornarNull() {
        // Arrange
        when(jdbc.query(
            contains("FROM subtokens"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(Collections.emptyList());

        // Act
        SubtokenPriceResponse result = subtokenService.obtenerPrecioConDetalle(proyectoId);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void obtenerPrecioConDetalle_debeRetornarResponseCorrecto() {
        // Arrange
        Map<String, Object> mockSubtoken = new HashMap<>();
        mockSubtoken.put("id", subtokenId);
        mockSubtoken.put("precio_base", new BigDecimal("1.00"));
        mockSubtoken.put("suministro_total", 1000);
        mockSubtoken.put("cupo_restante", 400);
        mockSubtoken.put("factor_volatilidad", new BigDecimal("0.50"));
        mockSubtoken.put("contract_address", "0xABC");

        when(jdbc.query(
            contains("FROM subtokens"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockSubtoken));

        Map<String, Object> mockProject = new HashMap<>();
        mockProject.put("estado", "EJECUCION");
        when(jdbc.query(
            contains("FROM projects"),
            any(RowMapper.class),
            eq(proyectoId)
        )).thenReturn(List.of(mockProject));

        // Act
        SubtokenPriceResponse response = subtokenService.obtenerPrecioConDetalle(proyectoId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getProyectoId()).isEqualTo(proyectoId);
        assertThat(response.getPrecioActual()).isEqualByComparingTo("1.95");
        assertThat(response.getPrecioBase()).isEqualByComparingTo("1.00");
        assertThat(response.getSuministroTotal()).isEqualTo(1000);
        assertThat(response.getCupoRestante()).isEqualTo(400);
        assertThat(response.getFactorDemanda()).isEqualByComparingTo("0.30");
        assertThat(response.getFactorRendimiento()).isEqualByComparingTo("0.50");
    }

    @Test
    void updateQuotaAndPrice_debeLlamarUpdateEnJdbc() {
        // Act
        subtokenService.updateQuotaAndPrice(subtokenId, 50, new BigDecimal("2.50"));

        // Assert
        verify(jdbc).update(
            eq("UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ? WHERE id = ?"),
            eq(50), eq(new BigDecimal("2.50")), eq(subtokenId)
        );
    }

    @Test
    void addPortfolioEntry_debeLlamarUpdateEnJdbc() {
        // Act
        subtokenService.addPortfolioEntry(usuarioId, subtokenId, 20);

        // Assert
        verify(jdbc).update(
            anyString(),
            eq(usuarioId), eq(subtokenId), eq(20), eq(20)
        );
    }

    @Test
    void removePortfolioEntry_debeLlamarUpdateEnJdbc() {
        // Act
        subtokenService.removePortfolioEntry(usuarioId, proyectoId, 15);

        // Assert
        verify(jdbc).update(
            anyString(),
            eq(15), eq(proyectoId), eq(usuarioId)
        );
    }
}
