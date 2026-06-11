package com.systeam.project.service;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.investment.service.SmartContractService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class BoostServiceTest {

    private static final Long PROYECTO_ID = 1L;
    private static final Long USUARIO_ID = 10L;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private SmartContractService smartContractService;

    private BoostService service;

    @BeforeEach
    void setUp() {
        service = new BoostService(jdbc, smartContractService);
    }

    @Nested
    @DisplayName("boostProject")
    class BoostProject {

        @Test
        void cuandoProyectoNoExiste_lanzaResourceNotFound() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn(null);

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Proyecto no encontrado");
        }

        @Test
        void cuandoYaDestacado_lanzaConflict() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("RECOLECTANDO");
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("es_destacado FROM projects")),
                eq(Boolean.class), eq(PROYECTO_ID)))
                .thenReturn(true);

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está destacado");
        }

        @Test
        void cuandoSaldoInsuficiente_lanzaConflict() {
            stubsExito();
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("saldo_idea FROM users")),
                eq(BigDecimal.class), eq(USUARIO_ID)))
                .thenReturn(new BigDecimal("99.99"));

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Saldo insuficiente");
        }

        @Test
        void cuandoSaldoNull_lanzaConflict() {
            stubsExito();
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("saldo_idea FROM users")),
                eq(BigDecimal.class), eq(USUARIO_ID)))
                .thenReturn(null);

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Saldo insuficiente");
        }

        @Test
        void cuandoSaldoExacto_ejecutaBoost() {
            stubsExito();
            stubsBlockchainOk();
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("saldo_idea FROM users")),
                eq(BigDecimal.class), eq(USUARIO_ID)))
                .thenReturn(new BigDecimal("100.00"));

            service.boostProject(PROYECTO_ID, USUARIO_ID);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE users") && sql.toString().contains("saldo_idea")),
                eq(new BigDecimal("100.00")), eq(USUARIO_ID)
            );
            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE projects") && sql.toString().contains("es_destacado")),
                eq(new BigDecimal("100.00")), eq(PROYECTO_ID)
            );
        }

        @Test
        void conSaldoSuficiente_ejecutaBoost() {
            stubsExito();
            stubsBlockchainOk();
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("saldo_idea FROM users")),
                eq(BigDecimal.class), eq(USUARIO_ID)))
                .thenReturn(new BigDecimal("500.00"));

            service.boostProject(PROYECTO_ID, USUARIO_ID);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE users")),
                eq(new BigDecimal("100.00")), eq(USUARIO_ID)
            );
            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE projects")),
                eq(new BigDecimal("100.00")), eq(PROYECTO_ID)
            );
        }

        @Test
        void cuandoBlockchainFalla_lanzaConflict() {
            stubsExito();
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("saldo_idea FROM users")),
                eq(BigDecimal.class), eq(USUARIO_ID)))
                .thenReturn(new BigDecimal("500.00"));
            when(smartContractService.boostProject()).thenReturn(Map.of("success", false, "txHash", "0x0", "note", "Error en boost on-chain"));

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Error en boost on-chain");
        }

        private void stubsExito() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("RECOLECTANDO");
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("es_destacado FROM projects")),
                eq(Boolean.class), eq(PROYECTO_ID)))
                .thenReturn(false);
        }

        private void stubsBlockchainOk() {
            when(smartContractService.boostProject()).thenReturn(Map.of("success", true, "txHash", "0xboosttx", "note", "Boost on-chain verificado en Sepolia"));
        }
    }

    @Nested
    @DisplayName("desboostProject")
    class DesboostProject {

        @Test
        void ejecutaDesboostCorrectamente() {
            service.desboostProject(PROYECTO_ID);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE projects") &&
                               sql.toString().contains("es_destacado = FALSE")),
                eq(PROYECTO_ID)
            );
        }
    }
}
