package com.systeam.investment.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.config.BlockchainProperties;

@ExtendWith(MockitoExtension.class)
class SmartContractServiceTest {

    private static final String CONTRACT_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TREASURY = "0xtreasury00000000000000000000000000000001";
    private static final String TOKEN_ADDRESS = "0xtoken0000000000000000000000000000000002";
    private static final Long PROYECTO_ID = 1L;
    private static final Long USUARIO_ID = 10L;

    @Mock
    private BlockchainService blockchain;

    @Mock
    private InvestmentSwapService investmentSwapService;

    private BlockchainProperties props;
    private SmartContractService service;

    @BeforeEach
    void setUp() {
        props = new BlockchainProperties();
        props.setProjectTokenAddress(TOKEN_ADDRESS);
        props.setTreasuryAddress(TREASURY);
        service = new SmartContractService(blockchain, props, investmentSwapService);
    }

    @Nested
    @DisplayName("getContractInfo")
    class GetContractInfo {

        @Test
        void retornaInfoCompleta() throws Exception {
            when(blockchain.balanceOf(CONTRACT_ADDRESS, TREASURY)).thenReturn(BigInteger.valueOf(1000));
            when(blockchain.getBackendAddress()).thenReturn("0xbackend");
            when(blockchain.getBalanceSepoliaInEth()).thenReturn(new BigDecimal("2.5"));
            when(blockchain.getLatestBlock()).thenReturn(BigInteger.valueOf(12345L));

            Map<String, Object> info = service.getContractInfo(CONTRACT_ADDRESS);

            assertThat(info.get("address")).isEqualTo(CONTRACT_ADDRESS);
            assertThat(info.get("treasuryBalance")).isEqualTo(BigInteger.valueOf(1000));
            assertThat(info.get("backendAddress")).isEqualTo("0xbackend");
            assertThat(info.get("backendSepoliaEth")).isEqualTo(new BigDecimal("2.5"));
            assertThat(info.get("latestBlock")).isEqualTo(BigInteger.valueOf(12345L));
            assertThat(info.get("note")).isEqualTo("Conexion a Sepolia activa");
        }

        @Test
        void cuandoBlockchainFalla_retornaError() throws Exception {
            when(blockchain.balanceOf(CONTRACT_ADDRESS, TREASURY))
                .thenThrow(new RuntimeException("Connection refused"));

            Map<String, Object> info = service.getContractInfo(CONTRACT_ADDRESS);

            assertThat(info.get("error")).isEqualTo("Connection refused");
            assertThat(info.get("note")).isEqualTo("Error de conexion con Sepolia");
        }
    }

    @Nested
    @DisplayName("getTotalSubTokens")
    class GetTotalSubTokens {

        @Test
        void retornaBalanceEnLong() throws Exception {
            when(blockchain.balanceOf(TOKEN_ADDRESS, TREASURY)).thenReturn(BigInteger.valueOf(5000));

            Long result = service.getTotalSubTokens(CONTRACT_ADDRESS);

            assertThat(result).isEqualTo(5000L);
        }

        @Test
        void cuandoBlockchainFalla_retornaCero() throws Exception {
            when(blockchain.balanceOf(TOKEN_ADDRESS, TREASURY))
                .thenThrow(new RuntimeException("Error de conexion"));

            Long result = service.getTotalSubTokens(CONTRACT_ADDRESS);

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("getTokenDistribution")
    class GetTokenDistribution {

        @Test
        void retornaDistribucionCompleta() throws Exception {
            when(blockchain.balanceOf(TOKEN_ADDRESS, TOKEN_ADDRESS)).thenReturn(BigInteger.valueOf(10000));
            when(blockchain.balanceOf(TOKEN_ADDRESS, TREASURY)).thenReturn(BigInteger.valueOf(3000));

            Map<String, Long> dist = service.getTokenDistribution(CONTRACT_ADDRESS);

            assertThat(dist.get("totalSupply")).isEqualTo(10000L);
            assertThat(dist.get("treasuryBalance")).isEqualTo(3000L);
        }

        @Test
        void cuandoBlockchainFalla_retornaVacios() throws Exception {
            when(blockchain.balanceOf(TOKEN_ADDRESS, TOKEN_ADDRESS))
                .thenThrow(new RuntimeException("Error"));

            Map<String, Long> dist = service.getTokenDistribution(CONTRACT_ADDRESS);

            assertThat(dist.get("disponible")).isZero();
            assertThat(dist.get("invertido")).isZero();
        }
    }

    @Nested
    @DisplayName("deployContract")
    class DeployContract {

        @Test
        void ejecutaMintYretornaDireccion() throws Exception {
            when(blockchain.mintTokens(TREASURY, BigInteger.valueOf(10000)))
                .thenReturn("0xmintx");

            String result = service.deployContract(PROYECTO_ID, 10000L, "TokenName");

            assertThat(result).isEqualTo(TOKEN_ADDRESS);
        }

        @Test
        void cuandoBlockchainFalla_retornaAddressFallback() throws Exception {
            when(blockchain.mintTokens(TREASURY, BigInteger.valueOf(10000)))
                .thenThrow(new RuntimeException("Error"));

            String result = service.deployContract(PROYECTO_ID, 10000L, "TokenName");

            assertThat(result).startsWith("0x");
            assertThat(result).contains(PROYECTO_ID.toString());
        }
    }

    @Nested
    @DisplayName("recordInvestment")
    class RecordInvestment {

        private static final String TX_HASH = "0xvalidtx";

        @Test
        void cuandoTxValida_retornaExito() throws Exception {
            when(blockchain.verifyTransaction(TX_HASH)).thenReturn(true);
            when(blockchain.getLatestBlock()).thenReturn(BigInteger.valueOf(500L));
            when(blockchain.balanceOf(TOKEN_ADDRESS, TREASURY)).thenReturn(BigInteger.valueOf(8000));

            Map<String, Object> result = service.recordInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("1000"), TX_HASH);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("txHash")).isEqualTo(TX_HASH);
            assertThat(result.get("blockNumber")).isEqualTo(500L);
            assertThat(result.get("note")).isEqualTo("Transaccion verificada en Sepolia");
            assertThat(result.get("treasuryTokensBalance")).isEqualTo(BigInteger.valueOf(8000));
        }

        @Test
        void cuandoTxInvalida_retornaExitoConNota() throws Exception {
            when(blockchain.verifyTransaction(TX_HASH)).thenReturn(false);

            Map<String, Object> result = service.recordInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("500"), TX_HASH);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("txHash")).isEqualTo(TX_HASH);
            assertThat(result.get("blockNumber")).isEqualTo(0L);
            assertThat(result.get("note")).isEqualTo("Tx no encontrada en Sepolia - se registra igual en DB");
        }

        @Test
        void cuandoBlockchainFalla_retornaModoOffline() throws Exception {
            when(blockchain.verifyTransaction(TX_HASH))
                .thenThrow(new RuntimeException("Red no disponible"));

            Map<String, Object> result = service.recordInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("200"), TX_HASH);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("txHash")).isEqualTo(TX_HASH);
            assertThat(result.get("blockNumber")).isEqualTo(0L);
            assertThat(((String) result.get("note"))).contains("Blockchain no disponible");
        }
    }

    @Nested
    @DisplayName("refundInvestment")
    class RefundInvestment {

        @Test
        void cuandoRefundExitoso_retornaTxHash() throws Exception {
            when(investmentSwapService.refund(PROYECTO_ID,
                new BigDecimal("500").multiply(new BigDecimal("10").pow(18)).toBigInteger(),
                TREASURY, TREASURY))
                .thenReturn("0xrefundx");

            Map<String, Object> result = service.refundInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("500"));

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("refundTxHash")).isEqualTo("0xrefundx");
            assertThat(result.get("note")).isEqualTo("Reembolso on-chain ejecutado en InvestmentSwap");
        }

        @Test
        void cuandoRefundRetornaNull_usaModoOffline() throws Exception {
            when(investmentSwapService.refund(PROYECTO_ID,
                new BigDecimal("300").multiply(new BigDecimal("10").pow(18)).toBigInteger(),
                TREASURY, TREASURY))
                .thenReturn(null);

            Map<String, Object> result = service.refundInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("300"));

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(((String) result.get("refundTxHash"))).contains("0xrefund-offline");
            assertThat(result.get("note")).isEqualTo("Reembolso en modo offline (InvestmentSwap no disponible)");
        }

        @Test
        void cuandoBlockchainFalla_retornaOffline() throws Exception {
            when(investmentSwapService.refund(PROYECTO_ID,
                new BigDecimal("100").multiply(new BigDecimal("10").pow(18)).toBigInteger(),
                TREASURY, TREASURY))
                .thenThrow(new RuntimeException("Error de conexion"));

            Map<String, Object> result = service.refundInvestment(
                PROYECTO_ID, USUARIO_ID, new BigDecimal("100"));

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(((String) result.get("refundTxHash"))).contains("0xrefund-");
            assertThat(((String) result.get("note"))).contains("modo offline");
        }
    }

    @Nested
    @DisplayName("deployInvestmentContract")
    class DeployInvestmentContract {

        @Test
        void retornaDireccionDelToken() {
            Map<String, Object> result = service.deployInvestmentContract(PROYECTO_ID);

            assertThat(result.get("contractAddress")).isEqualTo(TOKEN_ADDRESS);
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("note")).toString().contains("ya deployado");
        }
    }
}
