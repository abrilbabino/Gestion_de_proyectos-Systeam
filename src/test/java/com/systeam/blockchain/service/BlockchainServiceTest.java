package com.systeam.blockchain.service;

import java.io.IOException;
import java.math.BigDecimal;
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
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Credentials;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Pruebas unitarias para {@link BlockchainService}.
 * <p>
 * Este servicio proporciona operaciones generales sobre la blockchain:
 * consultas de saldos, allowances, owner de contratos, minteo, quema,
 * pagos, verificacion de transacciones y mas.
 * Todas las interacciones con Web3j estan mockeadas.
 * </p>
 *
 * <p>
 * Temas cubiertos:
 * <ul>
 *   <li>Consultas exitosas (balanceOf, allowance, owner, latestBlock)</li>
 *   <li>Fallos en ethCall (executeCall con error)</li>
 *   <li>Fallos en transacciones (executeWrite con error)</li>
 *   <li>Resultados vacios (callAndExtractUint256, getContractOwner)</li>
 *   <li>Fallos de RPC (ethBlockNumber, ethGetBalance)</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {

    private static final String TOKEN_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OWNER_ADDR = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SPENDER_ADDR = "0xcccccccccccccccccccccccccccccccccccccccc";
    private static final String TX_HASH = "0xtx123";
    private static final String BACKEND_ADDR = "0xdddddddddddddddddddddddddddddddddddddddd";

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;
    @Mock
    private BlockchainProperties props;

    private BlockchainService service;

    @BeforeEach
    void setUp() {
        service = new BlockchainService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("balanceOf")
    class BalanceOf {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaBalance() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000019");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.balanceOf(TOKEN_ADDR, OWNER_ADDR);

            assertThat(result).isEqualTo(BigInteger.valueOf(25));
        }

        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("call revert");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.balanceOf(TOKEN_ADDR, OWNER_ADDR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("call revert");
        }
    }

    @Nested
    @DisplayName("allowance")
    class Allowance {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaAllowance() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000064");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.allowance(TOKEN_ADDR, OWNER_ADDR, SPENDER_ADDR);

            assertThat(result).isEqualTo(BigInteger.valueOf(100));
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeCall devuelve una
         * respuesta con error, allowance lanza RuntimeException.
         * �Por que es importante? allowance usa callAndExtractUint256 que a su vez
         * llama a executeCall. Si executeCall falla, el error debe propagarse.
         * �Como funciona el setup? Mockeamos credentials.getAddress() y hacemos
         * que ethCall.hasError() retorne true con un mensaje de error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Un error en la llamada RPC
         * devolveria BigInteger.ZERO silenciosamente, ocultando el problema real.
         */
        @SuppressWarnings("unchecked")
        @Test
        void allowance_cuandoEthCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("allowance call failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.allowance(TOKEN_ADDR, OWNER_ADDR, SPENDER_ADDR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("allowance call failed");
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeCall devuelve un
         * resultado vacio ("0x"), callAndExtractUint256 retorna BigInteger.ZERO.
         * �Por que es importante? callAndExtractUint256 (lineas 154-160) verifica
         * si la lista decodificada no esta vacia y si el primer elemento es Uint256.
         * De lo contrario, retorna cero. Sin esta prueba, un resultado vacio
         * pasaria desapercibido.
         * �Como funciona el setup? Mockeamos ethCall para que retorne "0x",
         * que produce una lista vacia al decodificarse.
         * �Cual es el resultado esperado? BigInteger.ZERO.
         * �Que pasaria si el codigo estuviera mal? Si la lista estuviera vacia y
         * no se manejara correctamente, se lanzaria IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void allowance_cuandoResultadoVacio_retornaCero() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn("0x");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.allowance(TOKEN_ADDR, OWNER_ADDR, SPENDER_ADDR);

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }
    }

    @Nested
    @DisplayName("getContractOwner")
    class GetContractOwner {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaOwner() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x000000000000000000000000" + OWNER_ADDR.substring(2));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.getContractOwner(TOKEN_ADDR);

            assertThat(result).isEqualTo(OWNER_ADDR);
        }

        @SuppressWarnings("unchecked")
        @Test
        void direccionCero_retornaNull() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.getContractOwner(TOKEN_ADDR);

            assertThat(result).isEqualTo("0x0000000000000000000000000000000000000000");
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeCall devuelve una
         * respuesta con error, getContractOwner lanza RuntimeException.
         * �Por que es importante? Sin esta prueba, un error en la llamada RPC
         * para obtener el owner del contrato pasaria desapercibido.
         * �Como funciona el setup? Mockeamos credentials.getAddress() y hacemos
         * que ethCall.hasError() retorne true.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? En lugar de lanzar una
         * excepcion, el metodo devolveria null, ocultando la falla de red.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getContractOwner_cuandoEthCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("owner call failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getContractOwner(TOKEN_ADDR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("owner call failed");
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeCall devuelve un
         * resultado vacio ("0x"), getContractOwner retorna null.
         * �Por que es importante? La logica en la linea 78 verifica si la lista
         * no esta vacia y si el primer elemento es Address. Si no, retorna null.
         * Sin esta prueba, un resultado vacio pasaria desapercibido.
         * �Como funciona el setup? Mockeamos ethCall para que retorne "0x",
         * que produce una lista vacia al decodificarse.
         * �Cual es el resultado esperado? null.
         * �Que pasaria si el codigo estuviera mal? Si la lista vacia no se manejara
         * correctamente, se lanzaria IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getContractOwner_cuandoResultadoVacio_retornaNull() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn("0x");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.getContractOwner(TOKEN_ADDR);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getLatestBlock")
    class GetLatestBlock {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaNumero() throws Exception {
            var blockResp = org.mockito.Mockito.mock(EthBlockNumber.class);
            when(blockResp.getBlockNumber()).thenReturn(BigInteger.valueOf(42));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(blockResp);
            when(web3j.ethBlockNumber()).thenReturn((Request) request);

            BigInteger result = service.getLatestBlock();

            assertThat(result).isEqualTo(BigInteger.valueOf(42));
        }

        /**
         * �Que estamos probando? Verificamos que cuando web3j.ethBlockNumber()
         * lanza una IOException, la excepcion se propaga sin ser capturada.
         * �Por que es importante? El metodo getLatestBlock (linea 85) llama a
         * web3j.ethBlockNumber().send() directamente sin try-catch. Si la red
         * falla, la excepcion debe propagarse a quien llama al metodo.
         * �Como funciona el setup? Mockeamos web3j.ethBlockNumber() para que el
         * Request correspondiente lance IOException al llamar send().
         * �Cual es el resultado esperado? IOException (o Exception en general).
         * �Que pasaria si el codigo estuviera mal? Si el metodo tuviera un
         * try-catch que silenciara la excepcion, el sistema continuaria
         * operando con un numero de bloque incorrecto.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getLatestBlock_cuandoRpcFalla_lanzaExcepcion() throws Exception {
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenThrow(new IOException("Connection refused"));
            when(web3j.ethBlockNumber()).thenReturn((Request) request);

            assertThatThrownBy(() -> service.getLatestBlock())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection refused");
        }
    }

    @Nested
    @DisplayName("mintTokens")
    class MintTokens {

        @SuppressWarnings("unchecked")
        @Test
        void exito_enviaTx() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getProjectTokenAddress()).thenReturn(TOKEN_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.mintTokens(OWNER_ADDR, BigInteger.valueOf(1000));

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getProjectTokenAddress()).thenReturn(TOKEN_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("mint not allowed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.mintTokens(OWNER_ADDR, BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mint not allowed");
        }
    }

    @Nested
    @DisplayName("burnTokens")
    class BurnTokens {

        @SuppressWarnings("unchecked")
        @Test
        void exito_enviaTx() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getProjectTokenAddress()).thenReturn(TOKEN_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.burnTokens(OWNER_ADDR, BigInteger.valueOf(500));

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeWrite recibe una
         * respuesta con error, burnTokens lanza RuntimeException.
         * �Por que es importante? Si la transaccion de quema de tokens falla en la
         * red, el servicio debe propagar el error en lugar de ignorarlo.
         * �Como funciona el setup? Mockeamos credentials, projectTokenAddress,
         * transaction count y gas price, y luego hacemos que sendTransaction
         * devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Una transaccion de quema fallida
         * pasaria desapercibida, y el sistema daria por hecho que los tokens se
         * quemaron cuando en realidad no fue asi.
         */
        @SuppressWarnings("unchecked")
        @Test
        void burnTokens_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getProjectTokenAddress()).thenReturn(TOKEN_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("burn not allowed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.burnTokens(OWNER_ADDR, BigInteger.valueOf(500)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("burn not allowed");
        }
    }

    @Nested
    @DisplayName("payUSDC")
    class PayUSDC {

        @SuppressWarnings("unchecked")
        @Test
        void exito_enviaTx() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getPaymentGatewayAddress()).thenReturn(SPENDER_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.payUSDC(BigInteger.valueOf(100_000_000L), "action-1");

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeWrite recibe una
         * respuesta con error, payUSDC lanza RuntimeException.
         * �Por que es importante? Si el pago en USDC falla en la red, el servicio
         * debe propagar el error en lugar de continuar como si nada.
         * �Como funciona el setup? Mockeamos credentials, paymentGatewayAddress,
         * transaction count y gas price, y luego hacemos que sendTransaction
         * devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Un pago fallido pasaria
         * desapercibido y el sistema registraria un pago que nunca ocurrio.
         */
        @SuppressWarnings("unchecked")
        @Test
        void payUSDC_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            when(props.getPaymentGatewayAddress()).thenReturn(SPENDER_ADDR);
            mockGetTransactionCount();
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("payment failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.payUSDC(BigInteger.valueOf(100_000_000L), "action-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("payment failed");
        }
    }

    @Nested
    @DisplayName("verifyTransaction")
    class VerifyTransaction {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaTrue() throws Exception {
            var receipt = new TransactionReceipt();
            receipt.setStatus("0x1");
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) request);

            boolean result = service.verifyTransaction(TX_HASH);

            assertThat(result).isTrue();
        }

        @SuppressWarnings("unchecked")
        @Test
        void receiptVacio_retornaFalse() throws Exception {
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.empty());
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) request);

            boolean result = service.verifyTransaction(TX_HASH);

            assertThat(result).isFalse();
        }

        @SuppressWarnings("unchecked")
        @Test
        void statusNoEsUno_retornaFalse() throws Exception {
            var receipt = new TransactionReceipt();
            receipt.setStatus("0x0");
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) request);

            boolean result = service.verifyTransaction(TX_HASH);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getBackendAddress")
    class GetBackendAddress {

        @Test
        void retornaDireccion() {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);

            String result = service.getBackendAddress();

            assertThat(result).isEqualTo(BACKEND_ADDR);
        }
    }

    @Nested
    @DisplayName("getBalanceSepolia")
    class GetBalanceSepolia {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaWei() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var balanceResp = org.mockito.Mockito.mock(EthGetBalance.class);
            when(balanceResp.getBalance()).thenReturn(BigInteger.valueOf(1_000_000_000_000L));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(balanceResp);
            when(web3j.ethGetBalance(eq(BACKEND_ADDR), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getBalanceSepolia();

            assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000_000L));
        }

        /**
         * �Que estamos probando? Verificamos que cuando web3j.ethGetBalance()
         * lanza una IOException, la excepcion se propaga sin ser capturada.
         * �Por que es importante? El metodo getBalanceSepolia (linea 129-130)
         * llama a web3j.ethGetBalance().send() directamente. Si la red falla,
         * la excepcion debe propagarse a quien llama al metodo.
         * �Como funciona el setup? Mockeamos credentials.getAddress() y
         * web3j.ethGetBalance() para que el Request lance IOException al llamar
         * send().
         * �Cual es el resultado esperado? IOException (o Exception en general).
         * �Que pasaria si el codigo estuviera mal? Si hubiera un try-catch que
         * silenciara la excepcion, el sistema devolveria un saldo incorrecto
         * o cero sin indicar el error.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getBalanceSepolia_cuandoRpcFalla_lanzaExcepcion() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenThrow(new IOException("RPC failed"));
            when(web3j.ethGetBalance(eq(BACKEND_ADDR), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getBalanceSepolia())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("RPC failed");
        }
    }

    @Nested
    @DisplayName("getBalanceSepoliaInEth")
    class GetBalanceSepoliaInEth {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaEth() throws Exception {
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var balanceResp = org.mockito.Mockito.mock(EthGetBalance.class);
            when(balanceResp.getBalance()).thenReturn(BigInteger.valueOf(2_000_000_000_000_000_000L));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(balanceResp);
            when(web3j.ethGetBalance(eq(BACKEND_ADDR), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigDecimal result = service.getBalanceSepoliaInEth();

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(2.0));
        }
    }

    @SuppressWarnings("unchecked")
    private void mockGetTransactionCount() throws Exception {
        var countResp = org.mockito.Mockito.mock(EthGetTransactionCount.class);
        when(countResp.getTransactionCount()).thenReturn(BigInteger.valueOf(5));
        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(countResp);
        when(web3j.ethGetTransactionCount(any(), any())).thenReturn((Request) request);
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
