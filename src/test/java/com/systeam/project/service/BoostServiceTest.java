package com.systeam.project.service;

import java.math.BigDecimal;

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
    private static final String TX_HASH = "0xtxhash";

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

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID, TX_HASH))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Proyecto no encontrado");
        }

        @Test
        void cuandoTxHashDuplicado_lanzaConflict() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("FINANCIAMIENTO");

            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("COUNT(*) FROM project_boosts")),
                eq(Integer.class), eq(TX_HASH)))
                .thenReturn(1);

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID, TX_HASH))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue registrada");
        }

        @Test
        void cuandoBlockchainFalla_lanzaConflict() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("FINANCIAMIENTO");

            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("COUNT(*) FROM project_boosts")),
                eq(Integer.class), eq(TX_HASH)))
                .thenReturn(0);

            when(smartContractService.verifyBoostTransfer(TX_HASH)).thenReturn(false);

            assertThatThrownBy(() -> service.boostProject(PROYECTO_ID, USUARIO_ID, TX_HASH))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no fue encontrada o falló");
        }

        @Test
        void conTxValida_ejecutaBoost() {
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("estado FROM projects")),
                eq(String.class), eq(PROYECTO_ID)))
                .thenReturn("FINANCIAMIENTO");

            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("COUNT(*) FROM project_boosts")),
                eq(Integer.class), eq(TX_HASH)))
                .thenReturn(0);

            when(smartContractService.verifyBoostTransfer(TX_HASH)).thenReturn(true);

            // Mock gamification level
            when(jdbc.queryForObject(
                argThat(sql -> sql != null && sql.toString().contains("nivel_inversor FROM users")),
                eq(String.class), eq(USUARIO_ID)))
                .thenReturn("VISIONARY");

            service.boostProject(PROYECTO_ID, USUARIO_ID, TX_HASH);

            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("INSERT INTO project_boosts")),
                eq(PROYECTO_ID), eq(USUARIO_ID), eq(TX_HASH), eq(new BigDecimal("100.00"))
            );
            verify(jdbc).update(
                argThat(sql -> sql != null && sql.toString().contains("UPDATE projects")),
                eq(new BigDecimal("300.000")), eq(PROYECTO_ID)
            );
        }
    }
}
