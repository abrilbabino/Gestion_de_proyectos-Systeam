package com.systeam.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.testutil.TestDatabaseHelper;

class WalletRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private WalletRepository repository;

    @BeforeAll
    static void init() {
        ds = TestDatabaseHelper.createDataSource();
        jdbc = new JdbcTemplate(ds);
        TestDatabaseHelper.executeSchema(jdbc);
    }

    @AfterAll
    static void shutdown() {
        TestDatabaseHelper.closeDataSource(ds);
    }

    @BeforeEach
    void setUp() {
        repository = new WalletRepository(jdbc);
        jdbc.execute("DELETE FROM portfolio_activos");
        jdbc.execute("DELETE FROM subtokens");
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("""
            INSERT INTO users (id, nombre, saldo_idea, saldo_usdt) VALUES (1, 'User1', 5000.00, 1000.00)
            """);
        jdbc.execute("""
            INSERT INTO users (id, nombre, saldo_idea, saldo_usdt) VALUES (2, 'User2', 0.00, 0.00)
            """);
        jdbc.execute("INSERT INTO projects (id, creador_id, titulo, monto_requerido, plazo, estado, created_at, updated_at) VALUES (1, 1, 'Proj1', 10000, NOW(), 'FINANCIAMIENTO', NOW(), NOW())");
        jdbc.execute("""
            INSERT INTO subtokens (id, proyecto_id, nombre, simbolo, suministro_total, cupo_restante, precio_actual, precio_base, factor_volatilidad, contract_address, created_at, updated_at)
            VALUES (1, 1, 'TokenA', 'TKA', 1000, 800, 15.00, 10.00, 1.5, '0xabc', NOW(), NOW())
            """);
        jdbc.execute("""
            INSERT INTO portfolio_activos (id, usuario_id, subtoken_id, cantidad, created_at, updated_at)
            VALUES (1, 1, 1, 50, NOW(), NOW())
            """);
    }

    @Test
    void findSaldoIdea_shouldReturnBalance() {
        BigDecimal saldo = repository.findSaldoIdea(1L);
        assertThat(saldo).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void findSaldoIdea_shouldReturnZeroForUserWithNoBalance() {
        BigDecimal saldo = repository.findSaldoIdea(2L);
        assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findSaldoUsdt_shouldReturnBalance() {
        BigDecimal saldo = repository.findSaldoUsdt(1L);
        assertThat(saldo).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void findPortfolio_shouldReturnUserAssets() {
        List<Object[]> portfolio = repository.findPortfolio(1L);

        assertThat(portfolio).hasSize(1);
        Object[] row = portfolio.get(0);
        assertThat(row[0]).isEqualTo(1L);          // subtoken_id
        assertThat(row[1]).isEqualTo("Proj1");     // proyecto_nombre
        assertThat(row[2]).isEqualTo("TokenA");    // subtoken_nombre
        assertThat(row[3]).isEqualTo("TKA");       // subtoken_simbolo
        assertThat(row[4]).isEqualTo(50);          // cantidad
        assertThat((BigDecimal) row[5]).isEqualByComparingTo(new BigDecimal("15.00")); // precio_actual
    }

    @Test
    void findPortfolio_shouldReturnEmptyForUserWithNoAssets() {
        List<Object[]> portfolio = repository.findPortfolio(2L);
        assertThat(portfolio).isEmpty();
    }
}
