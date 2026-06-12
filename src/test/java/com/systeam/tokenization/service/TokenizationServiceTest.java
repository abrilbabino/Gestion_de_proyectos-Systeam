package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeafyFactoryService;
import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.blockchain.service.OfferingContractService;
import com.systeam.config.BlockchainProperties;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.tokenization.dto.CreateTokenRequest;
import com.systeam.tokenization.dto.TokenResponse;
import com.systeam.tokenization.repository.TokenizationRepository;

@ExtendWith(MockitoExtension.class)
class TokenizationServiceTest {

    private static final Long PROYECTO_ID = 1L;
    private static final String TREASURY = "0xtreasury123456789012345678901234567890123456";
    private static final String TOKEN_ADDR = "0xtoken45678901234567890123456789012345678901";

    @Mock
    private BlockchainService blockchainService;
    @Mock
    private IdeafyFactoryService ideafyFactoryService;
    @Mock
    private InvestmentSwapService investmentSwapService;
    @Mock
    private TokenFactoryService tokenFactoryService;
    @Mock
    private TokenizationRepository tokenizationRepository;
    @Mock
    private OfferingContractService offeringContractService;
    @Mock
    private JdbcTemplate jdbc;

    private BlockchainProperties blockchainProperties;
    private TokenizationService service;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setTreasuryAddress(TREASURY);
        blockchainProperties.setProjectTokenAddress(TOKEN_ADDR);
        service = new TokenizationService(blockchainService, ideafyFactoryService,
            investmentSwapService, tokenFactoryService, tokenizationRepository,
            offeringContractService, blockchainProperties, jdbc);
    }

    @Nested
    @DisplayName("crearTokenParaProyecto")
    class CrearTokenParaProyecto {

        @Test
        void viaIdeafyFactory_creaTokenYRegistraOffering() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(eq(PROYECTO_ID), anyInt(), anyInt(),
                eq(TREASURY), anyString(), anyString(), any(BigInteger.class)))
                .thenReturn(TOKEN_ADDR);
            when(offeringContractService.registerOffering(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("0xtxhash");
            when(blockchainService.verifyTransaction("0xtxhash")).thenReturn(true);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Mi Proyecto", "MP",
                1000, new BigDecimal("10"), new BigDecimal("50000"),
                LocalDateTime.now().plusDays(30));

            assertThat(result).isEqualTo(TOKEN_ADDR);
            verify(tokenizationRepository).save(eq(PROYECTO_ID), anyString(), anyString(),
                eq(1000), eq(new BigDecimal("10")), any(BigDecimal.class), eq(TOKEN_ADDR));
            verify(blockchainService).verifyTransaction("0xtxhash");
        }

        @Test
        void viaIdeafyFactory_tokenYaExiste() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Existente", "EX",
                500, new BigDecimal("5"), null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
            verify(tokenizationRepository).save(eq(PROYECTO_ID), anyString(), anyString(),
                eq(500), eq(new BigDecimal("5")), any(BigDecimal.class), eq(TOKEN_ADDR));
        }

        @Test
        void ideafyFactoryFalla_fallbackInvestmentSwap() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenThrow(new RuntimeException("Error"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(investmentSwapService.crearTokenProyecto(eq(PROYECTO_ID), anyString(), anyString(),
                any(BigInteger.class))).thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Fallback", "FB",
                200, new BigDecimal("2"), null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void investmentSwapYaTieneToken() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Error deploy"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "ExistenteSwap", "ES",
                100, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void todosLosBlockchainFallan_lanzaConflictException() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenThrow(new RuntimeException("Error1"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenThrow(new RuntimeException("Error2"));
            when(tokenFactoryService.crearTokenProyecto(any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Error3"));

            assertThatThrownBy(() -> service.crearTokenParaProyecto(PROYECTO_ID, "Cero", "C0",
                    100, BigDecimal.ONE, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No se pudo crear el token");

            verify(tokenizationRepository, never()).save(any(), any(), any(), anyInt(), any(), any(), any());
        }

        @Test
        void cupoMaximoNull_usa100000() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(eq(PROYECTO_ID), anyInt(), anyInt(),
                eq(TREASURY), anyString(), anyString(), eq(BigInteger.valueOf(100000).multiply(BigInteger.TEN.pow(18)))))
                .thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Sin cupo", "SC",
                null, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
            verify(tokenizationRepository).save(eq(PROYECTO_ID), anyString(), anyString(),
                eq(100000), eq(BigDecimal.ONE), any(BigDecimal.class), eq(TOKEN_ADDR));
        }

        @Test
        void simboloGeneradoCuandoNull() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(eq(PROYECTO_ID), anyInt(), anyInt(),
                eq(TREASURY), anyString(), eq("p1"), any()))
                .thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Sin simbolo", null,
                100, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void tituloAcortadoSiEsLargo() throws Exception {
            String tituloLargo = "Este es un titulo extremadamente largo para un proyecto";
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(eq(PROYECTO_ID), anyInt(), anyInt(),
                eq(TREASURY), eq("Este es un titulo..."), anyString(), any()))
                .thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, tituloLargo, "TL",
                100, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void montoRequeridoNull_omiteRegistrarOffering() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(TOKEN_ADDR);

            service.crearTokenParaProyecto(PROYECTO_ID, "Sin monto", "SM",
                100, BigDecimal.ONE, null, null);

            verify(offeringContractService, never()).registerOffering(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void conInvestmentSwapComoPrimeraOpcion() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Error deploy"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(investmentSwapService.crearTokenProyecto(eq(PROYECTO_ID), anyString(), anyString(),
                any())).thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Swap main", "SW",
                100, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void conTokenFactoryComoUltimoFallback() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenThrow(new RuntimeException("Error1"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenThrow(new RuntimeException("Error2"));
            when(tokenFactoryService.crearTokenProyecto(eq(PROYECTO_ID), anyString(), anyString(),
                any())).thenReturn(TOKEN_ADDR);

            String result = service.crearTokenParaProyecto(PROYECTO_ID, "Factory FB", "FF",
                100, BigDecimal.ONE, null, null);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }
    }

    @Nested
    @DisplayName("crearToken")
    class CrearToken {

        @Test
        void conDatosDeProyecto_completo() throws Exception {
            CreateTokenRequest request = new CreateTokenRequest();
            request.setProyectoId(PROYECTO_ID);
            request.setCupoMaximoTokens(500);
            request.setValorNominal(new BigDecimal("25"));

            when(jdbc.queryForMap(anyString(), eq(PROYECTO_ID)))
                .thenReturn(Map.of(
                    "simbolo", "TEST",
                    "monto_requerido", new BigDecimal("100000"),
                    "plazo", java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(30))
                ));
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(TOKEN_ADDR);
            when(offeringContractService.registerOffering(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("0xtxhash");
            when(blockchainService.verifyTransaction("0xtxhash")).thenReturn(true);
            when(tokenizationRepository.findByProjectId(PROYECTO_ID))
                .thenReturn(Optional.of(Map.ofEntries(
                    Map.entry("id", 1L), Map.entry("proyecto_id", PROYECTO_ID),
                    Map.entry("nombre", "Test"), Map.entry("simbolo", "TEST"),
                    Map.entry("suministro_total", 500), Map.entry("cupo_restante", 500),
                    Map.entry("precio_actual", new BigDecimal("25")),
                    Map.entry("precio_base", new BigDecimal("25")),
                    Map.entry("factor_volatilidad", new BigDecimal("0.50")),
                    Map.entry("contract_address", TOKEN_ADDR),
                    Map.entry("proyectoTitulo", "Proyecto #1")
                )));

            TokenResponse response = service.crearToken(request);

            assertThat(response.getSimbolo()).isEqualTo("TEST");
            assertThat(response.getProyectoId()).isEqualTo(PROYECTO_ID);
        }

        @Test
        void cuandoProyectoNoTieneDatos_usaDefault() throws Exception {
            CreateTokenRequest request = new CreateTokenRequest();
            request.setProyectoId(PROYECTO_ID);
            request.setCupoMaximoTokens(100);
            request.setValorNominal(BigDecimal.ONE);

            when(jdbc.queryForMap(anyString(), eq(PROYECTO_ID)))
                .thenThrow(new RuntimeException("Not found"));
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(ideafyFactoryService.launchProject(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(TOKEN_ADDR);
            when(tokenizationRepository.findByProjectId(PROYECTO_ID))
                .thenReturn(Optional.of(Map.ofEntries(
                    Map.entry("id", 2L), Map.entry("proyecto_id", PROYECTO_ID),
                    Map.entry("nombre", "Default"), Map.entry("simbolo", "p1"),
                    Map.entry("suministro_total", 100), Map.entry("cupo_restante", 100),
                    Map.entry("precio_actual", BigDecimal.ONE),
                    Map.entry("precio_base", BigDecimal.ONE),
                    Map.entry("factor_volatilidad", new BigDecimal("0.50")),
                    Map.entry("contract_address", TOKEN_ADDR),
                    Map.entry("proyectoTitulo", "Proyecto #1")
                )));

            TokenResponse response = service.crearToken(request);

            assertThat(response.getSimbolo()).isEqualTo("p1");
        }
    }

    @Nested
    @DisplayName("obtenerTokenPorProyecto")
    class ObtenerTokenPorProyecto {

        @Test
        void cuandoExiste_retornaResponse() {
            when(tokenizationRepository.findByProjectId(PROYECTO_ID))
                .thenReturn(Optional.of(Map.ofEntries(
                    Map.entry("id", 1L), Map.entry("proyecto_id", PROYECTO_ID),
                    Map.entry("nombre", "Token"), Map.entry("simbolo", "TKN"),
                    Map.entry("suministro_total", 1000), Map.entry("cupo_restante", 500),
                    Map.entry("precio_actual", new BigDecimal("50")),
                    Map.entry("precio_base", new BigDecimal("50")),
                    Map.entry("factor_volatilidad", new BigDecimal("0.50")),
                    Map.entry("contract_address", TOKEN_ADDR),
                    Map.entry("proyectoTitulo", "Proyecto #1")
                )));

            TokenResponse response = service.obtenerTokenPorProyecto(PROYECTO_ID);

            assertThat(response.getSimbolo()).isEqualTo("TKN");
        }

        @Test
        void cuandoNoExiste_lanzaResourceNotFound() {
            when(tokenizationRepository.findByProjectId(PROYECTO_ID))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.obtenerTokenPorProyecto(PROYECTO_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No se encontro token");
        }
    }

    @Nested
    @DisplayName("obtenerDireccionToken")
    class ObtenerDireccionToken {

        @Test
        void ideafyFactoryDevuelveDireccion() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(TOKEN_ADDR);

            String result = service.obtenerDireccionToken(PROYECTO_ID);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void ideafyFactoryNull_investmentSwapDevuelve() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(TOKEN_ADDR);

            String result = service.obtenerDireccionToken(PROYECTO_ID);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void investmentSwapDevuelveCero_tokenFactoryDevuelve() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(null);
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID))
                .thenReturn("0x0000000000000000000000000000000000000000");
            when(tokenFactoryService.obtenerTokenDeProyecto(PROYECTO_ID)).thenReturn(TOKEN_ADDR);

            String result = service.obtenerDireccionToken(PROYECTO_ID);

            assertThat(result).isEqualTo(TOKEN_ADDR);
        }

        @Test
        void todosFallan_retornaNull() throws Exception {
            when(ideafyFactoryService.obtenerTokenDeProyecto(PROYECTO_ID))
                .thenThrow(new RuntimeException("Error1"));
            when(investmentSwapService.obtenerTokenDeProyecto(PROYECTO_ID))
                .thenThrow(new RuntimeException("Error2"));
            when(tokenFactoryService.obtenerTokenDeProyecto(PROYECTO_ID))
                .thenThrow(new RuntimeException("Error3"));

            String result = service.obtenerDireccionToken(PROYECTO_ID);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("listarTokens")
    class ListarTokens {

        @Test
        void retornaPaginaDeTokens() {
            PageRequest pageable = PageRequest.of(0, 10);
            when(tokenizationRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(
                    Map.ofEntries(
                        Map.entry("id", 1L), Map.entry("proyecto_id", PROYECTO_ID),
                        Map.entry("nombre", "Token1"), Map.entry("simbolo", "TK1"),
                        Map.entry("suministro_total", 100), Map.entry("cupo_restante", 50),
                        Map.entry("precio_actual", BigDecimal.TEN),
                        Map.entry("precio_base", BigDecimal.TEN),
                        Map.entry("factor_volatilidad", new BigDecimal("0.50")),
                        Map.entry("contract_address", TOKEN_ADDR),
                        Map.entry("proyectoTitulo", "Proyecto #1")
                    )
                ), pageable, 1));

            Page<TokenResponse> result = service.listarTokens(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSimbolo()).isEqualTo("TK1");
        }
    }
}
