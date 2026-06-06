package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.crypto.Credentials;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Tests para DividendDistributorService — el puente entre Java y el smart contract
 * DividendDistributor en la blockchain.
 * <p>
 * ¿Qué hace DividendDistributorService?
 * Permite distribuir dividendos entre los inversores de un proyecto (distribute),
 * reclamar dividendos (claim), consultar el monto reclamable (getClaimable)
 * y consultar el dividendo por token (getDividendPerToken).
 * <p>
 * ¿Cómo testeamos sin una blockchain real?
 * Usamos Mockito para simular Web3j, TransactionManager y BlockchainProperties.
 * - Para transacciones (distribute, claim): mockeamos txManager.sendTransaction()
 *   que devuelve un EthSendTransaction simulado.
 * - Para consultas (getClaimable, getDividendPerToken): mockeamos web3j.ethCall()
 *   que devuelve un EthCall simulado.
 * <p>
 * Errores comunes que estos tests previenen:
 * - Llamar a la blockchain sin haber configurado la dirección del contrato
 * - No detectar errores en la respuesta de la blockchain
 * - Usar dirección zero (0x0000...0000) como si fuera válida
 * - Asumir que la transacción siempre tiene éxito
 */
@ExtendWith(MockitoExtension.class)
class DividendDistributorServiceTest {

    private static final String DISTRIBUTOR_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String USER_ADDR = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TX_HASH = "0xtx123";
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final BigInteger PROYECTO_ID = BigInteger.ONE;

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;
    @Mock
    private BlockchainProperties props;

    private DividendDistributorService service;

    @BeforeEach
    void setUp() {
        service = new DividendDistributorService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("distribute")
    class Distribute {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.distribute(PROYECTO_ID, BigInteger.valueOf(1_000_000));

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void noConfigurado_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.distribute(PROYECTO_ID, BigInteger.valueOf(1_000_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        /**
         * ¿Qué estamos probando?
         * Llamar a distribute() cuando la dirección del contrato DividendDistributor
         * es la dirección zero (0x0000...0000).
         * <p>
         * ¿Por qué es importante?
         * La dirección zero no es un contrato real en la blockchain. Si no la filtramos,
         * intentaríamos enviar una transacción a una dirección inválida, lo que
         * desperdiciaría gas y fallaría silenciosamente.
         * <p>
         * ¿Cómo funciona el setup?
         * Configuramos props.getDividendDistributorAddress() para que devuelva
         * la dirección zero. No mockeamos nada más porque assertConfigured()
         * lanza la excepción antes de llegar a sendTransaction().
         * <p>
         * ¿Cuál es el resultado esperado?
         * IllegalStateException con el mensaje "DividendDistributor no configurado".
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Si assertConfigured() no detectara la dirección zero, el servicio
         * intentaría enviar una transacción a 0x0000...0000, lo que resulta
         * en una transacción fallida en la blockchain y gas perdido.
         */
        @Test
        void distribute_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.distribute(PROYECTO_ID, BigInteger.valueOf(1_000_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("distribute failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.distribute(PROYECTO_ID, BigInteger.valueOf(1_000_000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("distribute failed");
        }
    }

    @Nested
    @DisplayName("claim")
    class Claim {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.claim(PROYECTO_ID);

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void noConfigurado_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.claim(PROYECTO_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        /**
         * ¿Qué estamos probando?
         * Llamar a claim() cuando la dirección del contrato es la dirección zero.
         * <p>
         * ¿Por qué es importante?
         * La dirección zero no es un contrato válido. Reclamar dividendos contra
         * una dirección inválida desperdiciaría gas y nunca tendría éxito.
         * <p>
         * ¿Cómo funciona el setup?
         * Configuramos props.getDividendDistributorAddress() = ZERO_ADDRESS.
         * assertConfigured() detecta la dirección zero y lanza la excepción.
         * <p>
         * ¿Cuál es el resultado esperado?
         * IllegalStateException con mensaje "DividendDistributor no configurado".
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * El servicio enviaría una transacción claim a 0x0000...0000,
         * perdiendo gas y sin reclamar dividendos.
         */
        @Test
        void claim_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.claim(PROYECTO_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        /**
         * ¿Qué estamos probando?
         * claim() cuando la transacción en la blockchain falla (hasError = true).
         * <p>
         * ¿Por qué es importante?
         * Una transacción puede fallar por muchas razones: gas insuficiente,
         * el contrato revierte la llamada, etc. El servicio debe detectarlo
         * y lanzar una excepción, no quedarse en silencio.
         * <p>
         * ¿Cómo funciona el setup?
         * Mockeamos props.getDividendDistributorAddress() con una dirección válida,
         * mockeamos web3j.ethGasPrice() para el gas price,
         * y mockeamos txManager.sendTransaction() para devolver una respuesta
         * con hasError() = true y un mensaje de error.
         * <p>
         * ¿Cuál es el resultado esperado?
         * RuntimeException con el mensaje de error de la blockchain.
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Si el servicio ignorara response.hasError() y devolviera el txHash
         * aunque la transacción hubiera fallado, el sistema pensaría que
         * la reclamación fue exitosa cuando en realidad no lo fue.
         * Este bug causaría inconsistencias entre el estado en Java y
         * el estado real en la blockchain.
         */
        @Test
        void claim_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("claim failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.claim(PROYECTO_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("claim failed");
        }
    }

    @Nested
    @DisplayName("getClaimable")
    class GetClaimable {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaMonto() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x00000000000000000000000000000000000000000000000000000000000003e8");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getClaimable(PROYECTO_ID, USER_ADDR);

            assertThat(result).isEqualTo(BigInteger.valueOf(1000));
        }

        @SuppressWarnings("unchecked")
        @Test
        void resultadoVacio_retornaCero() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getClaimable(PROYECTO_ID, USER_ADDR);

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }

        /**
         * ¿Qué estamos probando?
         * getClaimable() cuando la dirección del contrato es la dirección zero.
         * <p>
         * ¿Por qué es importante?
         * Consultar un contrato en la dirección zero no tiene sentido y fallaría.
         * <p>
         * ¿Cómo funciona el setup?
         * Configuramos props.getDividendDistributorAddress() = ZERO_ADDRESS.
         * assertConfigured() lanza IllegalStateException antes de ejecutar ethCall.
         * <p>
         * ¿Cuál es el resultado esperado?
         * IllegalStateException con mensaje "DividendDistributor no configurado".
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Se haría una consulta ethCall a 0x0000...0000 que devolvería
         * datos basura o fallaría, consumiendo recursos innecesariamente.
         */
        @Test
        void getClaimable_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.getClaimable(PROYECTO_ID, USER_ADDR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        /**
         * ¿Qué estamos probando?
         * getClaimable() cuando la llamada ethCall a la blockchain falla
         * (hasError = true).
         * <p>
         * ¿Por qué es importante?
         * Una consulta ethCall puede fallar si el contrato no existe,
         * la función revertea, o hay un problema de conexión. El servicio
         * debe propagar el error, no ignorarlo.
         * <p>
         * ¿Cómo funciona el setup?
         * Mockeamos props.getDividendDistributorAddress() con dirección válida,
         * credentials.getAddress() con la dirección del usuario,
         * y web3j.ethCall() para devolver un EthCall con hasError() = true
         * y un mensaje de error simulado.
         * <p>
         * ¿Cuál es el resultado esperado?
         * RuntimeException con el mensaje de error del ethCall.
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Si el servicio ignorara response.hasError() en executeCall(),
         * intentaría decodificar una respuesta vacía o inválida,
         * causando una excepción críptica (NullPointerException,
         * IndexOutOfBounds, etc.) en lugar de un mensaje claro.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getClaimable_cuandoEthCallFalla_lanzaRuntime() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("revert: not enough dividends");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getClaimable(PROYECTO_ID, USER_ADDR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revert");
        }
    }

    @Nested
    @DisplayName("getDividendPerToken")
    class GetDividendPerToken {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaMonto() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x000000000000000000000000000000000000000000000000000000000000000a");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getDividendPerToken(PROYECTO_ID);

            assertThat(result).isEqualTo(BigInteger.valueOf(10));
        }

        @SuppressWarnings("unchecked")
        @Test
        void resultadoVacio_retornaCero() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getDividendPerToken(PROYECTO_ID);

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }

        /**
         * ¿Qué estamos probando?
         * getDividendPerToken() cuando la dirección del contrato es la dirección zero.
         * <p>
         * ¿Por qué es importante?
         * Consultar dividendPerToken en la dirección zero no tiene sentido.
         * <p>
         * ¿Cómo funciona el setup?
         * Configuramos props.getDividendDistributorAddress() = ZERO_ADDRESS.
         * assertConfigured() lanza la excepción.
         * <p>
         * ¿Cuál es el resultado esperado?
         * IllegalStateException con mensaje "DividendDistributor no configurado".
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Se haría una consulta ethCall a 0x0000...0000 consumiendo recursos
         * y probablemente devolviendo un resultado incorrecto.
         */
        @Test
        void getDividendPerToken_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getDividendDistributorAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.getDividendPerToken(PROYECTO_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DividendDistributor");
        }

        /**
         * ¿Qué estamos probando?
         * getDividendPerToken() cuando la llamada ethCall falla (hasError = true).
         * <p>
         * ¿Por qué es importante?
         * Misma razón que getClaimable: el servicio debe detectar errores
         * en consultas a la blockchain.
         * <p>
         * ¿Cómo funciona el setup?
         * Mockeamos props, credentials y web3j.ethCall() para que devuelva
         * un EthCall con error.
         * <p>
         * ¿Cuál es el resultado esperado?
         * RuntimeException con el mensaje de error del ethCall.
         * <p>
         * ¿Qué pasaría si el código estuviera mal?
         * Si executeCall() no verificara hasError(), se intentaría decodificar
         * una respuesta inválida, resultando en excepciones difíciles de depurar.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getDividendPerToken_cuandoEthCallFalla_lanzaRuntime() throws Exception {
            when(props.getDividendDistributorAddress()).thenReturn(DISTRIBUTOR_ADDR);
            when(credentials.getAddress()).thenReturn(USER_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("revert: not available");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getDividendPerToken(PROYECTO_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revert");
        }
    }

    @SuppressWarnings("unchecked")
    private void mockGasPrice() throws Exception {
        var gp = org.mockito.Mockito.mock(EthGasPrice.class);
        when(gp.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(gp);
        when(web3j.ethGasPrice()).thenReturn((Request) request);
    }

    @SuppressWarnings("unchecked")
    private void mockSendTransaction(String txHash) throws Exception {
        var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(false);
        when(txResponse.getTransactionHash()).thenReturn(txHash);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);
    }
}
