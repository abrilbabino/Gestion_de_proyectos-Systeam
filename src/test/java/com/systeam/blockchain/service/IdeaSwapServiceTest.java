package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

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
 * Pruebas unitarias para {@link IdeaSwapService}.
 * <p>
 * Este servicio interactúa con el contrato IdeaSwap en la blockchain para
 * intercambiar tokens IDEA por USDC y viceversa, así como gestionar liquidez.
 * Todas las interacciones con Web3j, Credentials, TransactionManager y
 * BlockchainProperties están mockeadas para aislar la lógica del servicio.
 * </p>
 *
 * <p>
 * Temas cubiertos:
 * <ul>
 *   <li>Envio exitoso de transacciones (swap, liquidez)</li>
 *   <li>Validacion de direccion (null y cero)</li>
 *   <li>Fallos en transacciones (sendTransaction con error)</li>
 *   <li>Fallos en llamadas ethCall</li>
 *   <li>Extraccion de valores Uint256 y manejo de resultados vacios</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class IdeaSwapServiceTest {

    private static final String SWAP_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BACKEND_ADDR = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TX_HASH = "0xtx123";
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;
    @Mock
    private BlockchainProperties props;

    private IdeaSwapService service;

    @BeforeEach
    void setUp() {
        service = new IdeaSwapService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("swapUsdcForExactIdea")
    class SwapUsdcForExactIdea {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.swapUsdcForExactIdea(BigInteger.valueOf(100));

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void noConfigurado_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.swapUsdcForExactIdea(BigInteger.valueOf(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Enviar una transaccion a la direccion cero la perderia para siempre.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Los fondos se enviarian a la
         * direccion cero sin posibilidad de recuperacion.
         */
        @Test
        void swapUsdcForExactIdea_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.swapUsdcForExactIdea(BigInteger.valueOf(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando sendTransaction devuelve
         * una respuesta con error, el metodo lanza RuntimeException.
         * �Por que es importante? Si la transaccion falla en la red, el servicio
         * debe propagar el error para que quien lo llame pueda manejarlo.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress(), gas price,
         * y hacemos que txManager.sendTransaction devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Transacciones fallidas pasarian
         * desapercibidas y el sistema continuaria como si todo estuviera bien.
         */
        @SuppressWarnings("unchecked")
        @Test
        void swapUsdcForExactIdea_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("swap failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.swapUsdcForExactIdea(BigInteger.valueOf(100)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("swap failed");
        }
    }

    @Nested
    @DisplayName("swapIdeaForExactUsdc")
    class SwapIdeaForExactUsdc {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.swapIdeaForExactUsdc(BigInteger.valueOf(50));

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Enviar una transaccion a la direccion cero la perderia para siempre.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Los fondos se enviarian a la
         * direccion cero sin posibilidad de recuperacion.
         */
        @Test
        void swapIdeaForExactUsdc_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.swapIdeaForExactUsdc(BigInteger.valueOf(50)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando getIdeaSwapAddress() devuelve
         * null, el metodo lanza IllegalStateException.
         * �Por que es importante? El servicio no puede operar sin una direccion de
         * contrato configurada. Esta prueba asegura que el error se maneja
         * adecuadamente.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne null.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Un null pointer exception
         * reemplazaria al IllegalStateException, dando un error menos informativo.
         */
        @Test
        void swapIdeaForExactUsdc_noConfigurado_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.swapIdeaForExactUsdc(BigInteger.valueOf(50)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando sendTransaction devuelve
         * una respuesta con error, el metodo lanza RuntimeException.
         * �Por que es importante? Si la transaccion falla en la red, el servicio
         * debe propagar el error para que quien lo llame pueda manejarlo.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress(), gas price,
         * y hacemos que txManager.sendTransaction devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Transacciones fallidas pasarian
         * desapercibidas y el sistema continuaria como si todo estuviera bien.
         */
        @SuppressWarnings("unchecked")
        @Test
        void swapIdeaForExactUsdc_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("swap failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.swapIdeaForExactUsdc(BigInteger.valueOf(50)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("swap failed");
        }
    }

    @Nested
    @DisplayName("addLiquidity")
    class AddLiquidity {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.addLiquidity(BigInteger.valueOf(1000), BigInteger.valueOf(500));

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Enviar una transaccion a la direccion cero la perderia para siempre.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Los fondos se enviarian a la
         * direccion cero sin posibilidad de recuperacion.
         */
        @Test
        void addLiquidity_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.addLiquidity(BigInteger.valueOf(1000), BigInteger.valueOf(500)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando getIdeaSwapAddress() devuelve
         * null, el metodo lanza IllegalStateException.
         * �Por que es importante? El servicio no puede operar sin una direccion de
         * contrato configurada. Esta prueba asegura que el error se maneja
         * adecuadamente.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne null.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Un null pointer exception
         * reemplazaria al IllegalStateException, dando un error menos informativo.
         */
        @Test
        void addLiquidity_noConfigurado_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.addLiquidity(BigInteger.valueOf(1000), BigInteger.valueOf(500)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando sendTransaction devuelve
         * una respuesta con error, el metodo lanza RuntimeException.
         * �Por que es importante? Si la transaccion falla en la red, el servicio
         * debe propagar el error para que quien lo llame pueda manejarlo.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress(), gas price,
         * y hacemos que txManager.sendTransaction devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Transacciones fallidas pasarian
         * desapercibidas y el sistema continuaria como si todo estuviera bien.
         */
        @SuppressWarnings("unchecked")
        @Test
        void addLiquidity_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("add liquidity failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.addLiquidity(BigInteger.valueOf(1000), BigInteger.valueOf(500)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("add liquidity failed");
        }
    }

    @Nested
    @DisplayName("removeLiquidity")
    class RemoveLiquidity {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.removeLiquidity(BigInteger.valueOf(500), BigInteger.valueOf(250));

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Enviar una transaccion a la direccion cero la perderia para siempre.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Los fondos se enviarian a la
         * direccion cero sin posibilidad de recuperacion.
         */
        @Test
        void removeLiquidity_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.removeLiquidity(BigInteger.valueOf(500), BigInteger.valueOf(250)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando getIdeaSwapAddress() devuelve
         * null, el metodo lanza IllegalStateException.
         * �Por que es importante? El servicio no puede operar sin una direccion de
         * contrato configurada. Esta prueba asegura que el error se maneja
         * adecuadamente.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne null.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? Un null pointer exception
         * reemplazaria al IllegalStateException, dando un error menos informativo.
         */
        @Test
        void removeLiquidity_noConfigurado_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.removeLiquidity(BigInteger.valueOf(500), BigInteger.valueOf(250)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando sendTransaction devuelve
         * una respuesta con error, el metodo lanza RuntimeException.
         * �Por que es importante? Si la transaccion falla en la red, el servicio
         * debe propagar el error para que quien lo llame pueda manejarlo.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress(), gas price,
         * y hacemos que txManager.sendTransaction devuelva una respuesta con error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Transacciones fallidas pasarian
         * desapercibidas y el sistema continuaria como si todo estuviera bien.
         */
        @SuppressWarnings("unchecked")
        @Test
        void removeLiquidity_cuandoTxFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("remove liquidity failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.removeLiquidity(BigInteger.valueOf(500), BigInteger.valueOf(250)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("remove liquidity failed");
        }
    }

    @Nested
    @DisplayName("getIdeaReserve")
    class GetIdeaReserve {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaReserva() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000064");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getIdeaReserve();

            assertThat(result).isEqualTo(BigInteger.valueOf(100));
        }

        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("revert");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getIdeaReserve())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revert");
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Consultar una reserva en la direccion cero devolveria datos basura.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? El metodo intentaria hacer una
         * llamada ethCall a la direccion cero, resultando en un error confuso.
         */
        @Test
        void getIdeaReserve_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.getIdeaReserve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando ethCall devuelve un resultado
         * vacio ("0x"), el metodo extractUint256 retorna BigInteger.ZERO.
         * �Por que es importante? La funcion extractUint256 en la linea 128-131
         * verifica si la lista no esta vacia y si el primer elemento es Uint256.
         * Si no, retorna cero. Sin esta prueba, un resultado vacio causaria
         * un comportamiento indefinido.
         * �Como funciona el setup? Mockeamos ethCall para que retorne "0x",
         * que al ser decodificado produce una lista vacia.
         * �Cual es el resultado esperado? BigInteger.ZERO.
         * �Que pasaria si el codigo estuviera mal? Si extractUint256 no manejara
         * correctamente listas vacias, lanzaria un IndexOutOfBoundsException
         * o devolveria un valor incorrecto.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getIdeaReserve_cuandoResultadoVacio_retornaCero() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn("0x");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getIdeaReserve();

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }
    }

    @Nested
    @DisplayName("getUsdcReserve")
    class GetUsdcReserve {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaReserva() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x00000000000000000000000000000000000000000000000000000000000000c8");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getUsdcReserve();

            assertThat(result).isEqualTo(BigInteger.valueOf(200));
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * IdeaSwap es la direccion cero, el metodo lanza IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Consultar una reserva en la direccion cero devolveria datos basura.
         * �Como funciona el setup? Mockeamos props.getIdeaSwapAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "IdeaSwap".
         * �Que pasaria si el codigo estuviera mal? El metodo intentaria hacer una
         * llamada ethCall a la direccion cero, resultando en un error confuso.
         */
        @Test
        void getUsdcReserve_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getIdeaSwapAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.getUsdcReserve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaSwap");
        }

        /**
         * �Que estamos probando? Verificamos que cuando executeCall devuelve una
         * respuesta con error, el metodo lanza RuntimeException.
         * �Por que es importante? Si la llamada ethCall falla (por ejemplo, el
         * contrato no existe o la red esta caida), el servicio debe propagar el
         * error para que se maneje adecuadamente.
         * �Como funciona el setup? Mockeamos ethCall.hasError() para que retorne
         * true y configuramos un mensaje de error.
         * �Cual es el resultado esperado? RuntimeException con el mensaje del error.
         * �Que pasaria si el codigo estuviera mal? Un error en la llamada RPC
         * pasaria desapercibido y se devolveria un valor incorrecto.
         */
        @SuppressWarnings("unchecked")
        @Test
        void getUsdcReserve_cuandoEthCallFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("usdc call failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getUsdcReserve())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("usdc call failed");
        }
    }

    @Nested
    @DisplayName("getPrice")
    class GetPrice {

        @SuppressWarnings("unchecked")
        @Test
        void calculaPrecioCorrectamente() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall1 = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall1.hasError()).thenReturn(false);
            when(ethCall1.getValue()).thenReturn(
                "0x00000000000000000000000000000000000000000000000000000000000003e8");
            var ethCall2 = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall2.hasError()).thenReturn(false);
            when(ethCall2.getValue()).thenReturn(
                "0x000000000000000000000000000000000000000000000000000000000007a120");
            var request1 = org.mockito.Mockito.mock(Request.class);
            when(request1.send()).thenReturn(ethCall1);
            var request2 = org.mockito.Mockito.mock(Request.class);
            when(request2.send()).thenReturn(ethCall2);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request1, (Request) request2);

            BigInteger result = service.getPrice();

            assertThat(result).isEqualTo(BigInteger.valueOf(5_000_000));
        }

        @SuppressWarnings("unchecked")
        @Test
        void reservaIdeaCero_retornaCero() throws Exception {
            when(props.getIdeaSwapAddress()).thenReturn(SWAP_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getPrice();

            assertThat(result).isEqualTo(BigInteger.ZERO);
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
