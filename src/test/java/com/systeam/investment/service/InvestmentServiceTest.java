package com.systeam.investment.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import com.systeam.investment.dto.CreateInvestmentRequest;
import com.systeam.investment.dto.InvestmentResponse;
import com.systeam.investment.dto.ValidateInvestmentRequest;
import com.systeam.investment.dto.ValidateInvestmentResponse;
import com.systeam.investment.repository.InvestmentRepository;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.config.BlockchainProperties;
import com.systeam.project.service.SmartContractService;
import com.systeam.shared.model.Inversion;
import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestmentServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private SmartContractService smartContractService;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private DynamicPricingService pricingService;

    @Mock
    private InvestmentSwapService investmentSwapService;

    @Mock
    private BlockchainProperties blockchainProperties;

    @InjectMocks
    private InvestmentService investmentService;

    private Proyecto proyectoFinanciamiento;
    private Inversion inversionBase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(blockchainProperties.getTreasuryAddress()).thenReturn("0x7eEA865D2f47B5cC0fF4c8967C1cCf667fEBE50A");
        Usuario creador = new Usuario();
        creador.setId(1L);

        proyectoFinanciamiento = new Proyecto();
        proyectoFinanciamiento.setId(1L);
        proyectoFinanciamiento.setTitulo("Proyecto Test");
        proyectoFinanciamiento.setEstado("FINANCIAMIENTO");

        Usuario usuario = new Usuario();
        usuario.setId(2L);

        inversionBase = new Inversion();
        inversionBase.setId(1L);
        inversionBase.setUsuario(usuario);
        inversionBase.setProyecto(proyectoFinanciamiento);
        inversionBase.setMontoIdea(new BigDecimal("1000.00"));
        inversionBase.setSubTokensRecibidos(10);
        inversionBase.setTxHash("0xabc123");
        inversionBase.setEstado("CONFIRMADA");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateInvestment_cuandoProyectoValido_retornaValido() {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "cupo_restante", 100,
                "precio_actual", new BigDecimal("10.00"),
                "precio_base", new BigDecimal("10.00"),
                "suministro_total", 1000,
                "factor_volatilidad", new BigDecimal("0.50")
            )));

        when(pricingService.calcularPrecioDinamico(
            any(BigDecimal.class), anyInt(), anyInt(), any(BigDecimal.class)
        )).thenReturn(new BigDecimal("10.00"));

        ValidateInvestmentResponse response = investmentService.validateInvestment(request);

        assertThat(response.isValido()).isTrue();
        assertThat(response.getMensaje()).contains("valida");
        assertThat(response.getCupoDisponible()).isEqualTo(100);
        assertThat(response.getSubTokensARecebir()).isEqualTo(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateInvestment_cuandoProyectoNoEnFinanciamiento_retornaInvalido() {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "PREPARACION",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )));

        ValidateInvestmentResponse response = investmentService.validateInvestment(request);

        assertThat(response.isValido()).isFalse();
        assertThat(response.getMensaje()).contains("no esta en estado de financiamiento");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateInvestment_cuandoSinSubtoken_retornaInvalido() {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of());

        ValidateInvestmentResponse response = investmentService.validateInvestment(request);

        assertThat(response.isValido()).isFalse();
        assertThat(response.getMensaje()).contains("no tiene un subtoken");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateInvestment_cuandoSinCupo_retornaInvalido() {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "cupo_restante", 0,
                "precio_actual", new BigDecimal("10.00"),
                "precio_base", new BigDecimal("10.00"),
                "suministro_total", 1000,
                "factor_volatilidad", new BigDecimal("0.50")
            )));

        when(pricingService.calcularPrecioDinamico(
            any(BigDecimal.class), anyInt(), anyInt(), any(BigDecimal.class)
        )).thenReturn(new BigDecimal("10.00"));

        ValidateInvestmentResponse response = investmentService.validateInvestment(request);

        assertThat(response.isValido()).isFalse();
        assertThat(response.getMensaje()).contains("no tiene cupo disponible");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateInvestment_cuandoProyectoNoExiste_lanzaNotFound() {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(99L);
        request.setMontoIdea(new BigDecimal("500.00"));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(99L)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> investmentService.validateInvestment(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createInvestment_conDatosValidos_creaInversionCorrectamente() throws Exception {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));
        request.setTxHash("0xdef456");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "cupo_restante", 100,
                "precio_actual", new BigDecimal("10.00"),
                "precio_base", new BigDecimal("10.00"),
                "suministro_total", 1000,
                "factor_volatilidad", new BigDecimal("0.50")
            )));

        when(pricingService.calcularPrecioDinamico(
            any(BigDecimal.class), anyInt(), anyInt(), any(BigDecimal.class)
        )).thenReturn(new BigDecimal("10.00"));

        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(2L)))
            .thenReturn(new BigDecimal("5000.00"));

        when(investmentSwapService.invest(anyLong(), any(), any(), anyString()))
            .thenReturn("0xabc-swap-inv-05");

        when(smartContractService.recordInvestment(anyLong(), anyLong(), any(BigDecimal.class), anyString()))
            .thenReturn(Map.of("success", true, "txHash", "0xdef456"));

        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        when(investmentRepository.save(any(Inversion.class))).thenAnswer(inv -> {
            Inversion saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        when(jdbc.queryForObject(anyString(), eq(String.class), anyLong()))
            .thenReturn("Proyecto Test");

        InvestmentResponse response = investmentService.createInvestment(request, 2L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo("CONFIRMADA");
        assertThat(response.getMontoIdea()).isEqualByComparingTo("500.00");
        assertThat(response.getSubTokensRecibidos()).isEqualTo(50);
        assertThat(response.getTxHash()).startsWith("0xabc-swap-");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createInvestment_cuandoProyectoNoEnFinanciamiento_lanzaConflict() {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));
        request.setTxHash("0xdef456");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "PREPARACION",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )));

        assertThatThrownBy(() -> investmentService.createInvestment(request, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no esta en estado de financiamiento");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createInvestment_cuandoSinSubtoken_lanzaConflict() {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));
        request.setTxHash("0xdef456");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of());

        assertThatThrownBy(() -> investmentService.createInvestment(request, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no tiene un subtoken");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createInvestment_cuandoCupoInsuficiente_lanzaConflict() {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("50000.00"));
        request.setTxHash("0xdef456");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "cupo_restante", 10,
                "precio_actual", new BigDecimal("10.00"),
                "precio_base", new BigDecimal("10.00"),
                "suministro_total", 1000,
                "factor_volatilidad", new BigDecimal("0.50")
            )));

        when(pricingService.calcularPrecioDinamico(
            any(BigDecimal.class), anyInt(), anyInt(), any(BigDecimal.class)
        )).thenReturn(new BigDecimal("10.00"));

        assertThatThrownBy(() -> investmentService.createInvestment(request, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("supera el cupo");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createInvestment_cuandoSaldoInsuficiente_lanzaConflict() {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("500.00"));
        request.setTxHash("0xdef456");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "titulo", "Proyecto Test",
                "estado", "FINANCIAMIENTO",
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("0.00")
            )))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "cupo_restante", 100,
                "precio_actual", new BigDecimal("10.00"),
                "precio_base", new BigDecimal("10.00"),
                "suministro_total", 1000,
                "factor_volatilidad", new BigDecimal("0.50")
            )));

        when(pricingService.calcularPrecioDinamico(
            any(BigDecimal.class), anyInt(), anyInt(), any(BigDecimal.class)
        )).thenReturn(new BigDecimal("10.00"));

        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(2L)))
            .thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> investmentService.createInvestment(request, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void getInvestmentById_cuandoNoExiste_lanzaNotFound() {
        when(investmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.getInvestmentById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getInvestmentHistory_retornaHistorial() {
        when(investmentRepository.findByUsuarioId(eq(2L), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(investmentService.getInvestmentHistory(2L, org.springframework.data.domain.PageRequest.of(0, 10)))
            .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void processExpiredProjects_cuandoProyectoNoAlcanzaMeta_ejecutaReembolso() {
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("5000.00")
            )));

        when(investmentRepository.findPendingRefundsByProyectoId(1L))
            .thenReturn(List.of(inversionBase));

        when(smartContractService.refundInvestment(anyLong(), anyLong(), any(BigDecimal.class)))
            .thenReturn(Map.of("success", true));

        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
    
        when(investmentRepository.save(any(Inversion.class))).thenReturn(inversionBase);
    
        investmentService.processExpiredProjects();

        verify(investmentRepository).save(any(Inversion.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void processExpiredProjects_cuandoProyectoAlcanzaMeta_noEjecutaReembolso() {
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(Map.of(
                "id", 1L,
                "montoRequerido", new BigDecimal("10000.00"),
                "montoRecaudado", new BigDecimal("10000.00")
            )));

        investmentService.processExpiredProjects();

        verify(investmentRepository, org.mockito.Mockito.never())
            .save(any(Inversion.class));
    }
}
