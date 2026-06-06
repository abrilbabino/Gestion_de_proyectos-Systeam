package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Credentials;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Pruebas unitarias para {@link IdeafyFactoryService}.
 * <p>
 * Este servicio se encarga de interactuar con el contrato inteligente IdeafyFactory
 * en la blockchain. Las pruebas mockean las dependencias (Web3j, Credentials,
 * TransactionManager, BlockchainProperties) para verificar el comportamiento del
 * servicio sin necesidad de una conexión real a la red.
 * </p>
 *
 * <h2>Estructura de las pruebas</h2>
 * <ul>
 *   <li><strong>launchProject:</strong> Prueba el lanzamiento de proyectos y la
 *       extracci&oacute;n de direcciones desde eventos de transacciones.</li>
 *   <li><strong>obtenerTokenDeProyecto:</strong> Verifica la consulta del token
 *       asociado a un proyecto.</li>
 *   <li><strong>getSubTokenCount:</strong> Prueba la obtenci&oacute;n del n&uacute;mero de
 *       subtokens creados.</li>
 *   <li><strong>getSubTokensPaginated:</strong> Verifica la paginaci&oacute;n de subtokens.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IdeafyFactoryServiceTest {

    private static final String FACTORY_ADDR = "0x1234567890123456789012345678901234567890";
    private static final String PROJECT_TOKEN = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final String CREATED_TOKEN = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final String CREATOR = "0x9876543210987654321098765432109876543210";
    private static final Long PROYECTO_ID = 1L;

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;

    private BlockchainProperties props;
    private IdeafyFactoryService service;

    @BeforeEach
    void setUp() {
        props = new BlockchainProperties();
        props.setIdeafyFactoryAddress(FACTORY_ADDR);
        props.setProjectTokenAddress(PROJECT_TOKEN);
        service = new IdeafyFactoryService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("launchProject")
    class LaunchProject {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaDireccionDesdeLog() throws Exception {
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn("0xtx1");
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            mockReceiptWithLog("0x000000000000000000000000" + CREATED_TOKEN.substring(2), "0xtx1");

            String result = service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000));

            assertThat(result).isEqualTo(CREATED_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void txFalla_lanzaRuntime() throws Exception {
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("revert");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revert");
        }
    }

    @Nested
    @DisplayName("decodeTokenAddressFromLogs — receipt vacío, topics inválidos, evento no encontrado")
    class DecodeTokenAddressFromLogs {

        /**
         * ¿Qué estamos probando? Que cuando el receipt de la transacción está vacío
         * (no se encontr&oacute;), el m&eacute;todo decodeTokenAddressFromLogs lanza una excepci&oacute;n.
         * ¿Por qué es importante? Si la red blockchain no devuelve el receipt,
         * no podemos extraer la direcci&oacute;n del token. Sin esta validaci&oacute;n, el c&oacute;digo
         * intentar&iacute;a acceder a un Optional vac&iacute;o y lanzar&iacute;a NoSuchElementException.
         * ¿Cómo funciona el setup? Mockeamos gasPrice, la transacci&oacute;n exitosa, y
         * luego mockeamos ethGetTransactionReceipt para que retorne Optional.empty().
         * ¿Cuál es el resultado esperado? RuntimeException con mensaje "No receipt".
         * ¿Qué pasaría si el código estuviera mal? Si no validara el Optional vac&iacute;o,
         * lanzar&iacute;a NoSuchElementException en lugar de RuntimeException con mensaje claro.
         */
        @SuppressWarnings("unchecked")
        @Test
        void receiptVacio_lanzaRuntime() throws Exception {
            mockGasPrice();
            mockSendTransaction("0xtx1");
            mockReceiptEmpty("0xtx1");

            assertThatThrownBy(() -> service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No receipt");
        }

        /**
         * ¿Qué estamos probando? Que cuando un log tiene menos de 3 topics,
         * el m&eacute;todo decodeTokenAddressFromLogs lo salta y contin&uacute;a iterando.
         * ¿Por qué es importante? Los topics con &iacute;ndice &lt; 3 son obligatorios para
         * identificar el evento ProjectLaunched. Si no se salta, podr&iacute;amos acceder
         * a topic.get(2) y lanzar IndexOutOfBoundsException.
         * ¿Cómo funciona el setup? Mockeamos un log con un solo topic.
         * Como ning&uacute;n log tiene 3+ topics con PROJECT_LAUNCHED_TOPIC, el bucle termina.
         * ¿Cuál es el resultado esperado? RuntimeException con "ProjectLaunched event not found".
         * ¿Qué pasaría si el código estuviera mal? Si no verificara topics.size() &gt;= 3,
         * intentar&iacute;a acceder a topic.get(2) en un log con menos topics y lanzar&iacute;a
         * IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logConMenosDe3Topics_lanzaRuntime() throws Exception {
            mockGasPrice();
            mockSendTransaction("0xtx1");
            mockReceiptWithLogTopicsLessThan3("0xtx1");

            assertThatThrownBy(() -> service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ProjectLaunched event not found");
        }

        /**
         * ¿Qué estamos probando? Que cuando topic[2] (tokenAddrHex) tiene longitud
         * menor a 40 caracteres, el log se salta y se lanza excepci&oacute;n.
         * ¿Por qué es importante? Una direcci&oacute;n Ethereum v&aacute;lida tiene 40 caracteres hex
         * (sin incluir 0x). Si el topic est&aacute; malformado, no debemos retornar una direcci&oacute;n
         * inv&aacute;lida.
         * ¿Cómo funciona el setup? Mockeamos un log con topic[2] = "0x1234" (6 chars).
         * La condici&oacute;n tokenAddrHex.length() &gt;= 40 falla, se salta el log, y el bucle termina.
         * ¿Cuál es el resultado esperado? RuntimeException con "ProjectLaunched event not found".
         * ¿Qué pasaría si el código estuviera mal? Si no validara la longitud, retornar&iacute;a
         * "0x1234" como direcci&oacute;n del token, que es inv&aacute;lida y causar&iacute;a errores posteriores.
         */
        @SuppressWarnings("unchecked")
        @Test
        void topicConLengthMenorA40_lanzaRuntime() throws Exception {
            mockGasPrice();
            mockSendTransaction("0xtx1");
            mockReceiptWithMalformedTopic("0xtx1");

            assertThatThrownBy(() -> service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ProjectLaunched event not found");
        }

        /**
         * ¿Qué estamos probando? Que cuando ning&uacute;n log en el receipt tiene el topic
         * PROJECT_LAUNCHED_TOPIC, el m&eacute;todo lanza una excepci&oacute;n.
         * ¿Por qué es importante? La transacci&oacute;n podr&iacute;a minarse pero emitir un evento
         * diferente si el contrato cambi&oacute; o si la llamada fue a otro contrato.
         * Sin esta validaci&oacute;n, el m&eacute;todo retornar&iacute;a null silenciosamente.
         * ¿Cómo funciona el setup? Mockeamos un log con 3 topics pero topic[0] es "0x00...0",
         * no PROJECT_LAUNCHED_TOPIC. El bucle itera todos los logs y ninguno coincide.
         * ¿Cuál es el resultado esperado? RuntimeException con "ProjectLaunched event not found".
         * ¿Qué pasaría si el código estuviera mal? Si no lanzara excepci&oacute;n al no encontrar
         * el evento, retornar&iacute;a null y el llamador recibir&iacute;a NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void sinEventoProjectLaunched_lanzaRuntime() throws Exception {
            mockGasPrice();
            mockSendTransaction("0xtx1");
            mockReceiptWithWrongTopic0("0xtx1");

            assertThatThrownBy(() -> service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ProjectLaunched event not found");
        }

        /**
         * ¿Qué estamos probando? El camino completo de extracci&oacute;n de direcci&oacute;n desde
         * los logs cuando topic[2] contiene la direcci&oacute;n con padding (32 bytes).
         * Espec&iacute;ficamente probamos que substring(length - 40) extrae correctamente
         * los &uacute;ltimos 40 caracteres hexadecimales.
         * ¿Por qué es importante? Este es el camino feliz (happy path) de
         * decodeTokenAddressFromLogs. Si la l&oacute;gica de substring es incorrecta,
         * obtendr&iacute;amos direcciones mal formadas.
         * ¿Cómo funciona el setup? Mockeamos un log con topic[2] que tiene formato
         * completo de 32 bytes (66 chars con 0x). La direcci&oacute;n est&aacute; en los &uacute;ltimos 40
         * caracteres.
         * ¿Cuál es el resultado esperado? La direcci&oacute;n extra&iacute;da correctamente.
         * ¿Qué pasaría si el código estuviera mal? Si el &iacute;ndice de substring estuviera
         * mal calculado, retornar&iacute;a una direcci&oacute;n con ceros de m&aacute;s o truncada.
         */
        @SuppressWarnings("unchecked")
        @Test
        void exito_decodeAddressConPaddingCorrecto() throws Exception {
            mockGasPrice();
            mockSendTransaction("0xtx1");

            String expectedAddr = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            String topic2WithPadding = "0x000000000000000000000000" + expectedAddr.substring(2);
            var logEntry = new org.web3j.protocol.core.methods.response.Log();
            logEntry.setData("0x");
            logEntry.setTopics(List.of(
                IdeafyFactoryService.PROJECT_LAUNCHED_TOPIC,
                "0x0000000000000000000000000000000000000000000000000000000000000001",
                topic2WithPadding));
            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setLogs(List.of(logEntry));
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt("0xtx1")).thenReturn((Request) receiptRequest);

            String result = service.launchProject(PROYECTO_ID, 1, 4000,
                CREATOR, "Test", "TST", BigInteger.valueOf(1000));

            assertThat(result).isEqualTo(expectedAddr);
        }
    }

    @Nested
    @DisplayName("obtenerTokenDeProyecto")
    class ObtenerTokenDeProyecto {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaDireccionToken() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x000000000000000000000000" + CREATED_TOKEN.substring(2));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.obtenerTokenDeProyecto(PROYECTO_ID);

            assertThat(result).isEqualTo(CREATED_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void direccionCero_retornaNull() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.obtenerTokenDeProyecto(PROYECTO_ID);

            assertThat(result).isNull();
        }

        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("execution reverted");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.obtenerTokenDeProyecto(PROYECTO_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("execution reverted");
        }
    }

    @Nested
    @DisplayName("getSubTokenCount")
    class GetSubTokenCount {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaCantidad() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000005");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getSubTokenCount();

            assertThat(result).isEqualTo(BigInteger.valueOf(5));
        }

        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("revert");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getSubTokenCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revert");
        }

        /**
         * ¿Qué estamos probando? Que getSubTokenCount retorna BigInteger.ZERO cuando
         * el resultado de ethCall es una cadena vac&iacute;a ("0x") que no se puede decodificar
         * como Uint256.
         * ¿Por qué es importante? Si el contrato retorna datos vac&iacute;os o en formato
         * inesperado, el m&eacute;todo debe fallar de forma segura retornando cero en lugar de
         * lanzar una excepci&oacute;n.
         * ¿Cómo funciona el setup? Mockeamos ethCall para que retorne "0x" (sin datos).
         * El c&oacute;digo intenta decodificar como Uint256, falla, y cae al return BigInteger.ZERO.
         * ¿Cuál es el resultado esperado? BigInteger.ZERO.
         * ¿Qué pasaría si el código estuviera mal? Si no hubiera fallback, podr&iacute;a lanzar
         * una excepci&oacute;n de decodificaci&oacute;n o retornar null.
         */
        @SuppressWarnings("unchecked")
        @Test
        void resultadoVacio_retornaCero() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn("0x");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getSubTokenCount();

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }
    }

    @Nested
    @DisplayName("getSubTokensPaginated")
    class GetSubTokensPaginated {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaLista() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            // ABI-encoded (address[],uint256): offset=0x20, uint256=1, len=1, addr=CREATED_TOKEN
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000020"
              + "0000000000000000000000000000000000000000000000000000000000000001"
              + "0000000000000000000000000000000000000000000000000000000000000001"
              + "000000000000000000000000" + CREATED_TOKEN.substring(2));
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            var result = service.getSubTokensPaginated(BigInteger.ZERO, BigInteger.TEN);

            assertThat(result).isNotEmpty();
        }

        /**
         * ¿Qué estamos probando? Que getSubTokensPaginated lanza RuntimeException
         * cuando ethCall retorna un error.
         * ¿Por qué es importante? Si la llamada al contrato falla (por ejemplo, el
         * contrato no existe o la red est&aacute; ca&iacute;da), debemos propagar el error en lugar
         * de retornar datos basura.
         * ¿Cómo funciona el setup? Mockeamos ethCall con hasError=true y un mensaje
         * de error. El m&eacute;todo executeCall detecta el error y lanza excepci&oacute;n.
         * ¿Cuál es el resultado esperado? RuntimeException con el mensaje del error.
         * ¿Qué pasaría si el código estuviera mal? Si no verificara hasError(),
         * intentar&iacute;a decodificar una respuesta vac&iacute;a o basura y lanzar&iacute;a una excepci&oacute;n
         * de decodificaci&oacute;n dif&iacute;cil de interpretar.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn(CREATOR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("call failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getSubTokensPaginated(BigInteger.ZERO, BigInteger.TEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("call failed");
        }
    }

    @SuppressWarnings("unchecked")
    private void mockGasPrice() throws Exception {
        var request = org.mockito.Mockito.mock(Request.class);
        var gp = org.mockito.Mockito.mock(EthGasPrice.class);
        when(gp.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
        when(request.send()).thenReturn(gp);
        when(web3j.ethGasPrice()).thenReturn((Request) request);
    }

    @SuppressWarnings("unchecked")
    private void mockReceiptWithLog(String eventData, String txHash) throws Exception {
        String topic2 = "0x000000000000000000000000" + CREATED_TOKEN.substring(2);
        var logEntry = new org.web3j.protocol.core.methods.response.Log();
        logEntry.setData(eventData);
        logEntry.setTopics(List.of(IdeafyFactoryService.PROJECT_LAUNCHED_TOPIC,
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            topic2));
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of(logEntry));
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        var receiptRequest = org.mockito.Mockito.mock(Request.class);
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) receiptRequest);
    }

    @SuppressWarnings("unchecked")
    private void mockSendTransaction(String txHash) throws Exception {
        var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(false);
        when(txResponse.getTransactionHash()).thenReturn(txHash);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);
    }

    @SuppressWarnings("unchecked")
    private void mockReceiptEmpty(String txHash) throws Exception {
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.empty());
        var receiptRequest = org.mockito.Mockito.mock(Request.class);
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) receiptRequest);
    }

    @SuppressWarnings("unchecked")
    private void mockReceiptWithLogTopicsLessThan3(String txHash) throws Exception {
        var logEntry = new org.web3j.protocol.core.methods.response.Log();
        logEntry.setTopics(List.of(IdeafyFactoryService.PROJECT_LAUNCHED_TOPIC));
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of(logEntry));
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        var receiptRequest = org.mockito.Mockito.mock(Request.class);
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) receiptRequest);
    }

    @SuppressWarnings("unchecked")
    private void mockReceiptWithMalformedTopic(String txHash) throws Exception {
        var logEntry = new org.web3j.protocol.core.methods.response.Log();
        logEntry.setTopics(List.of(
            IdeafyFactoryService.PROJECT_LAUNCHED_TOPIC,
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0x1234"));
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of(logEntry));
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        var receiptRequest = org.mockito.Mockito.mock(Request.class);
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) receiptRequest);
    }

    @SuppressWarnings("unchecked")
    private void mockReceiptWithWrongTopic0(String txHash) throws Exception {
        var logEntry = new org.web3j.protocol.core.methods.response.Log();
        logEntry.setTopics(List.of(
            "0x0000000000000000000000000000000000000000000000000000000000000000",
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0x000000000000000000000000deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of(logEntry));
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        var receiptRequest = org.mockito.Mockito.mock(Request.class);
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) receiptRequest);
    }
}
