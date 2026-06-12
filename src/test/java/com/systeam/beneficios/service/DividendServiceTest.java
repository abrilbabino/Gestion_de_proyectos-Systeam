package com.systeam.beneficios.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.DividendDistributorService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.OracleBillingNotFoundException;
import com.systeam.project.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    private static final Long PROYECTO_ID = 1L;
    private static final Long USUARIO_ID = 10L;
    private static final String WALLET = "0xwallet123456789012345678901234567890123456";

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private DividendDistributorService dividendDistributorService;

    @Mock
    private BlockchainService blockchainService;

    private DividendService service;

    @BeforeEach
    void setUp() {
        service = spy(new DividendService(jdbc, dividendDistributorService, blockchainService));
    }

    @Nested
    @DisplayName("crearReparto")
    class CrearReparto {

        @Test
        void cuandoProyectoNoExiste_lanzaResourceNotFound() {
            when(jdbc.queryForObject(anyString(), eq(String.class), eq(PROYECTO_ID)))
                .thenReturn(null);

            assertThatThrownBy(() -> service.crearReparto(PROYECTO_ID, new BigDecimal("1000")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Proyecto no encontrado");
        }

        @Test
        void cuandoEstadoNoPermitido_lanzaConflict() {
            when(jdbc.queryForObject(anyString(), eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("RECOLECTANDO");

            assertThatThrownBy(() -> service.crearReparto(PROYECTO_ID, new BigDecimal("1000")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EJECUCION o FINALIZADO");
        }

        @Test
        void cuandoNoHaySubtokens_lanzaConflict() throws Exception {
            when(jdbc.queryForObject(anyString(), eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("EJECUCION");
            when(dividendDistributorService.distribute(any(), any())).thenReturn("0xdisttx");
            when(blockchainService.verifyTransaction("0xdisttx")).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(PROYECTO_ID)))
                .thenReturn(0);

            assertThatThrownBy(() -> service.crearReparto(PROYECTO_ID, new BigDecimal("1000")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No hay subtokens");
        }

        @Test
        void cuandoHaySubtokens_creaReparto() throws Exception {
            when(jdbc.queryForObject(anyString(), eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("EJECUCION");
            when(dividendDistributorService.distribute(any(), any())).thenReturn("0xdisttx");
            when(blockchainService.verifyTransaction("0xdisttx")).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(PROYECTO_ID)))
                .thenReturn(10);
            when(jdbc.queryForObject(anyString(), eq(Long.class), eq(PROYECTO_ID),
                eq(new BigDecimal("1000")), eq(new BigDecimal("100.0000")),
                eq("0xdisttx")))
                .thenReturn(42L);

            Long result = service.crearReparto(PROYECTO_ID, new BigDecimal("1000"));

            assertThat(result).isEqualTo(42L);
            verify(dividendDistributorService).distribute(
                eq(BigInteger.valueOf(PROYECTO_ID)),
                eq(new BigDecimal("1000").multiply(new BigDecimal("1000000000000000000")).toBigInteger())
            );
        }

        @Test
        void cuandoDistribuidorFalla_lanzaConflict() throws Exception {
            when(jdbc.queryForObject(anyString(), eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("FINALIZADO");
            when(dividendDistributorService.distribute(any(), any()))
                .thenThrow(new RuntimeException("On-chain no disponible"));

            assertThatThrownBy(() -> service.crearReparto(PROYECTO_ID, new BigDecimal("500")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Error al distribuir dividendos on-chain");
        }
    }

    @Nested
    @DisplayName("crearRepartoDesdeOraculo")
    class CrearRepartoDesdeOraculo {

        @Test
        void cuandoNoHayOracleBilling_lanzaExcepcion() {
            when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(PROYECTO_ID)))
                .thenThrow(new RuntimeException("No data"));

            assertThatThrownBy(() -> service.crearRepartoDesdeOraculo(PROYECTO_ID))
                .isInstanceOf(OracleBillingNotFoundException.class)
                .hasMessageContaining("No hay datos del oráculo");
        }

        @Test
        void conOracleBilling_usaBpsCalculado() throws Exception {
            Map<String, Object> billing = Map.of(
                "montoFacturado", new BigDecimal("50000"),
                "fechaReporte", java.time.LocalDateTime.now(),
                "oracleAddress", "0xoracle",
                "txHash", "0xtx"
            );
            when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(PROYECTO_ID)))
                .thenReturn(billing);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(PROYECTO_ID)))
                .thenReturn(3000);
            doReturn(7L).when(service).crearReparto(any(), any());

            Long result = service.crearRepartoDesdeOraculo(PROYECTO_ID);

            assertThat(result).isEqualTo(7L);
        }

        @Test
        void cuandoDividendBpsNull_usaDefault3000() throws Exception {
            Map<String, Object> billing = Map.of(
                "montoFacturado", new BigDecimal("10000"),
                "fechaReporte", java.time.LocalDateTime.now(),
                "oracleAddress", "0xoracle",
                "txHash", "0xtx"
            );
            when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(PROYECTO_ID)))
                .thenReturn(billing);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(PROYECTO_ID)))
                .thenReturn(null);
            doReturn(7L).when(service).crearReparto(any(), any());

            Long result = service.crearRepartoDesdeOraculo(PROYECTO_ID);

            assertThat(result).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("reclamarDividendos")
    class ReclamarDividendos {

        @Test
        void cuandoNoTieneActivos_lanzaConflict() throws Exception {
            when(dividendDistributorService.getClaimable(
                BigInteger.valueOf(PROYECTO_ID), WALLET)).thenReturn(BigInteger.ZERO);
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID), eq(PROYECTO_ID)))
                .thenReturn(List.of());

            assertThatThrownBy(() -> service.reclamarDividendos(PROYECTO_ID, USUARIO_ID, WALLET))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No tienes subtokens");
        }

        @Test
        void cuandoNoHayDividendos_lanzaConflict() throws Exception {
            when(dividendDistributorService.getClaimable(
                BigInteger.valueOf(PROYECTO_ID), WALLET)).thenReturn(BigInteger.ZERO);
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID), eq(PROYECTO_ID)))
                .thenReturn(List.of(Map.of("subtokenId", 5L, "cantidad", 10, "nombre", "TokenX")));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(PROYECTO_ID)))
                .thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> service.reclamarDividendos(PROYECTO_ID, USUARIO_ID, WALLET))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No hay dividendos");
        }

        @Test
        void reclamacionExitosa() throws Exception {
            when(dividendDistributorService.getClaimable(
                BigInteger.valueOf(PROYECTO_ID), WALLET)).thenReturn(BigInteger.valueOf(500));
            when(dividendDistributorService.claim(BigInteger.valueOf(PROYECTO_ID))).thenReturn("0xclaimtx");
            when(blockchainService.verifyTransaction("0xclaimtx")).thenReturn(true);
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID), eq(PROYECTO_ID)))
                .thenReturn(List.of(Map.of("subtokenId", 5L, "cantidad", 10, "nombre", "TokenX")));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(PROYECTO_ID)))
                .thenReturn(new BigDecimal("50.0000"));

            service.reclamarDividendos(PROYECTO_ID, USUARIO_ID, WALLET);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("INSERT INTO reclamos_dividendos")),
                eq(PROYECTO_ID), eq(USUARIO_ID), eq(5L), eq(10), eq(new BigDecimal("500.00"))
            );
            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE users")),
                eq(new BigDecimal("500.00")), eq(USUARIO_ID)
            );
        }

        @Test
        void reclamacionExitosa_sinWallet() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID), eq(PROYECTO_ID)))
                .thenReturn(List.of(Map.of("subtokenId", 3L, "cantidad", 2, "nombre", "TokenY")));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(PROYECTO_ID)))
                .thenReturn(new BigDecimal("25.0000"));

            service.reclamarDividendos(PROYECTO_ID, USUARIO_ID, null);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("INSERT INTO reclamos_dividendos")),
                eq(PROYECTO_ID), eq(USUARIO_ID), eq(3L), eq(2), eq(new BigDecimal("50.00"))
            );
        }
    }

    @Nested
    @DisplayName("consultarDividendosPendientes")
    class ConsultarDividendosPendientes {

        @Test
        void retornaMontoReclamable() throws Exception {
            when(dividendDistributorService.getClaimable(
                BigInteger.valueOf(PROYECTO_ID), WALLET))
                .thenReturn(BigInteger.valueOf(500L));

            BigInteger result = service.consultarDividendosPendientes(PROYECTO_ID, WALLET);

            assertThat(result).isEqualByComparingTo(BigInteger.valueOf(500));
        }

        @Test
        void cuandoWalletEsNull_retornaCero() {
            BigInteger result = service.consultarDividendosPendientes(PROYECTO_ID, null);

            assertThat(result).isZero();
        }

        @Test
        void cuandoWalletEsBlank_retornaCero() {
            BigInteger result = service.consultarDividendosPendientes(PROYECTO_ID, "   ");

            assertThat(result).isZero();
        }

        @Test
        void cuandoBlockchainFalla_retornaCero() throws Exception {
            when(dividendDistributorService.getClaimable(
                BigInteger.valueOf(PROYECTO_ID), WALLET))
                .thenThrow(new RuntimeException("Error"));

            BigInteger result = service.consultarDividendosPendientes(PROYECTO_ID, WALLET);

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("listarRepartos")
    class ListarRepartos {

        @Test
        void retornaListaDeRepartos() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(PROYECTO_ID)))
                .thenReturn(List.of(Map.of("id", 1L)));

            var result = service.listarRepartos(PROYECTO_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void cuandoNoHayRepartos_retornaVacio() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(PROYECTO_ID)))
                .thenReturn(List.of());

            var result = service.listarRepartos(PROYECTO_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listarReclamosUsuario")
    class ListarReclamosUsuario {

        @Test
        void retornaListaDeReclamos() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID)))
                .thenReturn(List.of(Map.of("id", 1L)));

            var result = service.listarReclamosUsuario(USUARIO_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void cuandoNoHayReclamos_retornaVacio() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(USUARIO_ID)))
                .thenReturn(List.of());

            var result = service.listarReclamosUsuario(USUARIO_ID);

            assertThat(result).isEmpty();
        }
    }
}
