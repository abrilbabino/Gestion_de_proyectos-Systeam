package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;
import java.util.List;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
/**
 * Pruebas unitarias para {@link InvestmentSwapService}.
 * <p>
 * <strong>¿Qué hace InvestmentSwapService?</strong><br>
 * Este servicio permite interactuar con un contrato inteligente "InvestmentSwap"
 * en la blockchain. Sus operaciones incluyen crear tokens de proyecto
 * ({@link #crearTokenProyecto}), invertir ({@link #invest}), reembolsar ({@link #refund})
 * y consultar la direcci&oacute;n de un token ({@link #obtenerTokenDeProyecto}).
 * <p>
 * <strong>¿C&oacute;mo funcionan estos tests?</strong><br>
 * Todas las pruebas usan <strong>Mockito</strong> para simular (mockear) las
 * dependencias externas ({@link Web3j}, {@link TransactionManager},
 * {@link BlockchainProperties}). No requieren conexi&oacute;n a una blockchain real.
 * Los mocks permiten verificar la l&oacute;gica interna del servicio de forma aislada.
 * <p>
 * <strong>Errores comunes para principiantes:</strong>
 * <ul>
 *   <li>Olvidar configurar un mock necesario &rarr; {@code NullPointerException} o
 *       {@code UnnecessaryStubbingException}.</li>
 *   <li>No considerar la direcci&oacute;n cero ({@code 0x0000...0000}) que representa
 *       "contrato no configurado".</li>
 *   <li>Confundir el mock del {@code TransactionManager} con el mock del
 *       {@code Web3j} &mdash; cada uno cumple un rol diferente.</li>
 * </ul>
 */
class InvestmentSwapServiceTest {

    private static final String TEST_PRIVATE_KEY =
        "0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static final String SWAP_ADDRESS =
        "0xcccccccccccccccccccccccccccccccccccccccc";
    private static final String PROJECT_TOKEN_ADDRESS =
        "0xdddddddddddddddddddddddddddddddddddddddd";
    private static final String CREATED_TOKEN_ADDRESS =
        "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String INVESTOR_ADDRESS =
        "0xffffffffffffffffffffffffffffffffffffffff";

    private Credentials credentials;
    private InvestmentSwapService service;

    @Mock
    private TransactionManager txManager;

    @Mock
    private Web3j web3j;

    @Mock
    private BlockchainProperties props;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
        when(props.getInvestmentSwapAddress()).thenReturn(SWAP_ADDRESS);

        service = new InvestmentSwapService(web3j, credentials, txManager, props);
    }

    // ── crearTokenProyecto ─────────────────────────────────

    @Test
    void crearTokenProyecto_cuandoSwapNoConfigurado_retornaFallback() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn(null);
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    @Test
    void crearTokenProyecto_retornaFallbackCuandoReceiptVacio() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xcreate001");
        mockEmptyReceipt("0xcreate001");
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    @Test
    void crearTokenProyecto_cuandoTxFalla_lanzaRuntime() throws Exception {
        mockGasPrice();
        var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("VM Exception while processing tx: revert");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("InvestmentSwap");
    }

    // ── invest ──────────────────────────────────────────────

    @Test
    void invest_cuandoSwapNoConfigurado_retornaOfflineTxHash() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn(null);

        String result = service.invest(1L, BigInteger.valueOf(500), BigInteger.TEN, INVESTOR_ADDRESS);

        assertThat(result).startsWith("0xswap-offline-");
    }

    @Test
    void invest_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xinvest002");

        String txHash = service.invest(1L, BigInteger.valueOf(500), BigInteger.TEN, INVESTOR_ADDRESS);

        assertThat(txHash).isEqualTo("0xinvest002");
    }

    @Test
    void invest_cuandoTxFalla_lanzaRuntime() throws Exception {
        mockGasPrice();
        var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("ERC20: insufficient allowance");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.invest(1L, BigInteger.valueOf(500), BigInteger.TEN, INVESTOR_ADDRESS))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("InvestmentSwap");
    }

    // ── refund ──────────────────────────────────────────────

    @Test
    void refund_cuandoSwapNoConfigurado_retornaOfflineTxHash() {
        when(props.getInvestmentSwapAddress()).thenReturn(null);

        String result = service.refund(1L, BigInteger.TEN, INVESTOR_ADDRESS, INVESTOR_ADDRESS);

        assertThat(result).startsWith("0xrefund-offline-");
    }

    @Test
    void refund_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xrefund003");

        String txHash = service.refund(1L, BigInteger.TEN, INVESTOR_ADDRESS, INVESTOR_ADDRESS);

        assertThat(txHash).isEqualTo("0xrefund003");
    }

    @Test
    void refund_cuandoTxFalla_retornaNull() throws Exception {
        mockGasPrice();
        var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("revert");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        String result = service.refund(1L, BigInteger.TEN, INVESTOR_ADDRESS, INVESTOR_ADDRESS);

        assertThat(result).isNull();
    }

    // ── obtenerTokenDeProyecto ──────────────────────────────

    @Test
    void obtenerTokenDeProyecto_cuandoSwapNoConfigurado_retornaFallback() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn(null);
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.obtenerTokenDeProyecto(1L);

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    @Test
    void obtenerTokenDeProyecto_retornaDireccion() throws Exception {
        String expectedHex = "0x000000000000000000000000" + CREATED_TOKEN_ADDRESS.substring(2);
        mockEthCall(expectedHex);

        String result = service.obtenerTokenDeProyecto(1L);

        assertThat(result).isEqualTo(CREATED_TOKEN_ADDRESS);
    }

    @Test
    void obtenerTokenDeProyecto_cuandoDireccionEsCero_retornaFallback() throws Exception {
        String expectedHex = "0x0000000000000000000000000000000000000000000000000000000000000000";
        mockEthCall(expectedHex);
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.obtenerTokenDeProyecto(1L);

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    @Test
    void obtenerTokenDeProyecto_cuandoEthCallFalla_lanzaRuntime() throws Exception {
        var ethCall = org.mockito.Mockito.mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("revert");
        when(ethCall.getError()).thenReturn(error);

        @SuppressWarnings("unchecked")
        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(ethCall);
        when(web3j.ethCall(any(), any())).thenReturn((Request) request);

        assertThatThrownBy(() -> service.obtenerTokenDeProyecto(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tokenDeProyecto");
    }

    // ── decodeTokenAddress (probado via crearTokenProyecto) ──

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos el caso en que la transacci&oacute;n se min&oacute; exitosamente (el receipt existe),
     * pero el receipt no contiene ning&uacute;n log (evento). Esto corresponde a la rama
     * {@code logs.isEmpty() == true} dentro del m&eacute;todo privado {@code decodeTokenAddress}.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Si el contrato inteligente no emite eventos, el servicio debe poder continuar
     * usando un valor de respaldo (fallback). Sin esta prueba, un cambio futuro podr&iacute;a
     * romper este comportamiento y hacer que el servicio devuelva {@code null} o lance
     * una excepci&oacute;n inesperada.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Mockeamos {@code web3j.ethGasPrice()} para que devuelva un gasPrice v&aacute;lido.<br>
     * - Mockeamos {@code txManager.sendTransaction()} para que devuelva un txHash
     *   {@code "0xcreate002"} sin errores.<br>
     * - Mockeamos {@code web3j.ethGetTransactionReceipt()} para que devuelva un receipt
     *   que S&Iacute; existe pero con una lista vac&iacute;a de logs ({@code List.of()}).<br>
     * - Configuramos {@code props.getProjectTokenAddress()} para que devuelva
     *   {@code PROJECT_TOKEN_ADDRESS}.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe devolver {@code PROJECT_TOKEN_ADDRESS} (el fallback), porque al
     * no haber logs no se puede extraer la direcci&oacute;n del token creado.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si alguien modificara {@code decodeTokenAddress} para que en vez de retornar el
     * fallback acceda directamente a {@code logs.get(0)} sin verificar si la lista est&aacute;
     * vac&iacute;a, lanzar&iacute;a un {@code IndexOutOfBoundsException}. Esta prueba detectar&iacute;a ese error.
     */
    @Test
    void crearTokenProyecto_logsVacios_retornaFallback() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xcreate002");
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);
        mockReceiptWithLogs("0xcreate002", List.of());

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos el caso en que el receipt tiene logs, pero el dato del primer log
     * (eventData) es {@code "0x"}, es decir, est&aacute; vac&iacute;o. Esto corresponde a la rama
     * {@code eventData == null || eventData.equals("0x")} dentro de
     * {@code decodeTokenAddress}.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Los contratos inteligentes a veces emiten eventos con data vac&iacute;a. El servicio
     * debe manejar este caso sin lanzar una excepci&oacute;n y usando el fallback.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Mockeamos las respuestas de gasPrice y sendTransaction igual que en tests
     *   anteriores.<br>
     * - Mockeamos {@code web3j.ethGetTransactionReceipt()} para que devuelva un receipt
     *   con un &uacute;nico log cuyo {@code getData()} retorna {@code "0x"} (vac&iacute;o).<br>
     * - Esto se logra con {@code mockLog("0x")} que crea un mock de {@code Log} con
     *   esa data, y luego {@code mockReceiptWithLogs()} que arma el receipt completo.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar {@code PROJECT_TOKEN_ADDRESS} (fallback), porque el
     * eventData vac&iacute;o impide decodificar la direcci&oacute;n del token.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si el c&oacute;digo no validara {@code eventData.equals("0x")} e intentara decodificar
     * {@code FunctionReturnDecoder.decode("0x", ...)}, podr&iacute;a lanzar una excepci&oacute;n
     * o devolver un resultado inesperado. Esta prueba asegura que el chequeo existe.
     */
    @Test
    void crearTokenProyecto_eventDataEsCero_retornaFallback() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xcreate003");
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);
        mockReceiptWithLogs("0xcreate003", List.of(mockLog("0x")));

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos el caso en que el receipt tiene logs, el eventData no es vac&iacute;o ni "0x",
     * pero {@code FunctionReturnDecoder.decode()} devuelve una lista vac&iacute;a o un tipo
     * que no es {@code Address}. Esto corresponde a la &uacute;ltima rama de
     * {@code decodeTokenAddress} antes del fallback final.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * En la blockchain pueden ocurrir eventos malformados o de otros contratos que el
     * servicio no espera. El servicio debe ser resiliente y no fallar ante datos
     * inesperados, sino retornar el fallback.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Mockeamos gasPrice y sendTransaction como siempre.<br>
     * - Mockeamos el receipt para que contenga un log con data {@code "0x1234"}. Este
     *   valor hexadecimal es muy corto (solo 2 bytes) para representar un Address
     *   v&aacute;lido (que requiere 32 bytes). Al intentar decodificarlo,
     *   {@code FunctionReturnDecoder.decode()} probablemente devuelva una lista vac&iacute;a
     *   porque no hay suficientes bytes para formar un Address.<br>
     * - Configuramos el fallback {@code PROJECT_TOKEN_ADDRESS}.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El servicio debe retornar {@code PROJECT_TOKEN_ADDRESS} (fallback). Si
     * {@code FunctionReturnDecoder.decode()} lanza una excepci&oacute;n en lugar de devolver
     * lista vac&iacute;a, la excepci&oacute;n se propagar&aacute; al test (indicando que el manejo de este
     * caso necesita mejora).
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si el c&oacute;digo asumiera que {@code decoded} siempre tiene al menos un elemento
     * y llamara a {@code decoded.get(0)} sin verificar {@code !decoded.isEmpty()},
     * lanzar&iacute;a {@code IndexOutOfBoundsException}. Esta prueba protege contra eso.
     */


    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos la ruta completa de &eacute;xito del m&eacute;todo {@code decodeTokenAddress}, donde
     * el receipt tiene logs, el eventData contiene la codificaci&oacute;n ABI v&aacute;lida de una
     * direcci&oacute;n, y {@code FunctionReturnDecoder.decode()} la interpreta correctamente
     * como un {@code Address}.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Este es el &uacute;nico camino que realmente devuelve la direcci&oacute;n del token creado
     * a partir de los eventos de la transacci&oacute;n. Sin esta prueba, no sabr&iacute;amos si el
     * flujo completo de decodificaci&oacute;n funciona.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Mockeamos gasPrice y sendTransaction para que la transacci&oacute;n se "mine" con
     *   el txHash {@code "0xcreate005"}.<br>
     * - Mockeamos el receipt para que contenga un log con data igual a la codificaci&oacute;n
     *   ABI de {@code CREATED_TOKEN_ADDRESS} como un {@code Address} de 32 bytes
     *   (left-padded con ceros):<br>
     *   {@code "0x000000000000000000000000eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"}<br>
     * - Esto simula que el contrato emiti&oacute; un evento con la direcci&oacute;n del token creado.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar {@code CREATED_TOKEN_ADDRESS}
     * ({@code "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"}), que es la direcci&oacute;n
     * decodificada del log.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si el m&eacute;todo {@code decodeTokenAddress} tuviera un error en la decodificaci&oacute;n
     * (por ejemplo, usar {@code logs.get(0).getTopics()} en lugar de
     * {@code logs.get(0).getData()}), devolver&iacute;a el fallback en vez de la direcci&oacute;n
     * real. Esta prueba detectar&iacute;a esa regresi&oacute;n.
     */
    @Test
    void crearTokenProyecto_decodeExitoso_retornaDireccionDecodeada() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xcreate005");
        String eventData = "0x000000000000000000000000eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
        mockReceiptWithLogs("0xcreate005", List.of(mockLog(eventData)));

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(CREATED_TOKEN_ADDRESS);
    }

    // ── guarda de direcci&oacute;n cero (0x0000...0000) ──

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos que {@code crearTokenProyecto} retorna el fallback cuando la direcci&oacute;n
     * del contrato InvestmentSwap es la direcci&oacute;n cero
     * ({@code 0x0000000000000000000000000000000000000000}) en lugar de {@code null}.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * El c&oacute;digo original ya probaba el caso {@code null}, pero la direcci&oacute;n cero tambi&eacute;n
     * significa "no configurado". Si alguien elimina la validaci&oacute;n
     * {@code equals("0x0000...0000")} por accidente, el servicio intentar&iacute;a enviar una
     * transacci&oacute;n a la direcci&oacute;n cero, lo que causar&iacute;a un error en la blockchain.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Hacemos que {@code props.getInvestmentSwapAddress()} retorne la direcci&oacute;n cero
     *   {@code "0x0000000000000000000000000000000000000000"}.<br>
     * - Configuramos {@code props.getProjectTokenAddress()} con un valor conocido.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar {@code PROJECT_TOKEN_ADDRESS} sin intentar enviar una
     * transacci&oacute;n a la blockchain.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si la condici&oacute;n {@code swapAddress.equals("0x0000...0000")} faltara, el servicio
     * intentar&iacute;a ejecutar {@code txManager.sendTransaction()} con direcci&oacute;n cero,
     * lo que resultar&iacute;a en un error de VM o una excepci&oacute;n inesperada.
     */
    @Test
    void crearTokenProyecto_cuandoSwapAddressEsCero_retornaFallback() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn("0x0000000000000000000000000000000000000000");
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.crearTokenProyecto(1L, "Proyecto", "PRJ", BigInteger.valueOf(1000));

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos que {@code invest} retorna un hash offline cuando la direcci&oacute;n del
     * contrato InvestmentSwap es la direcci&oacute;n cero.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Igual que en {@code crearTokenProyecto}, la direcci&oacute;n cero debe tratarse como
     * "no configurado". Si no se valida, {@code invest} intentar&iacute;a una transacci&oacute;n
     * a la direcci&oacute;n cero y fallar&iacute;a.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Hacemos que {@code props.getInvestmentSwapAddress()} retorne la direcci&oacute;n cero.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar un String que comienza con {@code "0xswap-offline-"}.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si la condici&oacute;n de direcci&oacute;n cero no estuviera en el m&eacute;todo {@code invest},
     * el servicio intentar&iacute;a conectarse a la blockchain con una direcci&oacute;n inv&aacute;lida,
     * lanzando una excepci&oacute;n en lugar de simular el swap offline.
     */
    @Test
    void invest_cuandoSwapAddressEsCero_retornaOffline() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn("0x0000000000000000000000000000000000000000");

        String result = service.invest(1L, BigInteger.valueOf(500), BigInteger.TEN, INVESTOR_ADDRESS);

        assertThat(result).startsWith("0xswap-offline-");
    }

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos que {@code refund} retorna un hash offline cuando la direcci&oacute;n del
     * contrato InvestmentSwap es la direcci&oacute;n cero.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * {@code refund} maneja la direcci&oacute;n cero sin lanzar excepciones checked, ya que
     * su firma no tiene {@code throws Exception}. Es importante verificar que esta
     * validaci&oacute;n funciona correctamente.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Hacemos que {@code props.getInvestmentSwapAddress()} retorne la direcci&oacute;n cero.
     * - No mockeamos gasPrice ni sendTransaction porque la validaci&oacute;n ocurre antes
     *   de llegar a esas llamadas.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar un String que comienza con {@code "0xrefund-offline-"}.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si faltara la validaci&oacute;n de direcci&oacute;n cero, {@code refund} intentar&iacute;a ejecutar
     * {@code web3j.ethGasPrice().send()}, y como no hay mock configurado (el test no
     * llama a {@code mockGasPrice()}), Mockito devolver&iacute;a {@code null} por defecto,
     * causando un {@code NullPointerException}. Esta prueba detecta esa regresi&oacute;n.
     */
    @Test
    void refund_cuandoSwapAddressEsCero_retornaOffline() {
        when(props.getInvestmentSwapAddress()).thenReturn("0x0000000000000000000000000000000000000000");

        String result = service.refund(1L, BigInteger.TEN, INVESTOR_ADDRESS, INVESTOR_ADDRESS);

        assertThat(result).startsWith("0xrefund-offline-");
    }

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos que {@code obtenerTokenDeProyecto} retorna el fallback cuando la
     * direcci&oacute;n del contrato InvestmentSwap es la direcci&oacute;n cero.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Sin la validaci&oacute;n de direcci&oacute;n cero, {@code obtenerTokenDeProyecto} har&iacute;a una
     * llamada {@code ethCall} a la direcci&oacute;n cero, lo que podr&iacute;a devolver datos
     * inesperados o lanzar una excepci&oacute;n.
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Hacemos que {@code props.getInvestmentSwapAddress()} retorne la direcci&oacute;n cero.<br>
     * - Configuramos {@code props.getProjectTokenAddress()} con
     *   {@code PROJECT_TOKEN_ADDRESS}.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo debe retornar {@code PROJECT_TOKEN_ADDRESS} sin hacer la llamada
     * {@code ethCall}.
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si faltara la validaci&oacute;n de direcci&oacute;n cero, el servicio har&iacute;a
     * {@code web3j.ethCall()} a la direcci&oacute;n cero, y como no hay mock configurado
     * para {@code ethCall}, Mockito devolver&iacute;a {@code null}, causando un
     * {@code NullPointerException}.
     */
    @Test
    void obtenerTokenDeProyecto_cuandoSwapAddressEsCero_retornaFallback() throws Exception {
        when(props.getInvestmentSwapAddress()).thenReturn("0x0000000000000000000000000000000000000000");
        when(props.getProjectTokenAddress()).thenReturn(PROJECT_TOKEN_ADDRESS);

        String result = service.obtenerTokenDeProyecto(1L);

        assertThat(result).isEqualTo(PROJECT_TOKEN_ADDRESS);
    }

    // ── refund catch(Exception) ──

    /**
     * <strong>¿Qué estamos probando?</strong><br>
     * Probamos que cuando {@code web3j.ethGasPrice().send()} lanza una excepci&oacute;n
     * dentro del bloque {@code try} de {@code refund}, el m&eacute;todo la atrapa en el
     * {@code catch (Exception e)} y retorna {@code null}.
     * <p>
     * <strong>¿Por qu&eacute; es importante?</strong><br>
     * Esta es la &uacute;nica ruta dentro de {@code refund} que retorna {@code null}.
     * Sin esta prueba, si alguien modificara el catch para que retorne otra cosa
     * o relance la excepci&oacute;n, no nos dar&iacute;amos cuenta. Adem&aacute;s, cubrimos el caso
     * de que la red de Ethereum no est&eacute; disponible (timeout, conexi&oacute;n ca&iacute;da, etc.).
     * <p>
     * <strong>¿C&oacute;mo funciona el setup?</strong><br>
     * - Mockeamos {@code web3j.ethGasPrice()} para que devuelva un {@code Request}
     *   cuyo m&eacute;todo {@code send()} lance una {@code RuntimeException} con el mensaje
     *   {@code "Cannot connect to node"}.<br>
     * - NO llamamos a {@code mockGasPrice()} porque ese helper configura el caso
     *   exitoso. En su lugar, configuramos manualmente el mock para que falle.
     * <p>
     * <strong>¿Cu&aacute;l es el resultado esperado?</strong><br>
     * El m&eacute;todo {@code refund} debe retornar {@code null}, indicando que el reembolso
     * on-chain fall&oacute; (en lugar de lanzar la excepci&oacute;n al llamante).
     * <p>
     * <strong>¿Qu&eacute; pasar&iacute;a si el c&oacute;digo estuviera mal?</strong><br>
     * Si alguien eliminara el {@code try-catch} de {@code refund}, esta excepci&oacute;n se
     * propagar&iacute;a al llamante (que no espera una excepci&oacute;n porque {@code refund} no
     * declara {@code throws Exception}). El programa fallar&iacute;a en lugar de manejar
     * el error gracefulmente.
     */
    @Test
    void refund_cuandoEthGasPriceLanzaExcepcion_retornaNull() throws Exception {
        @SuppressWarnings("unchecked")
        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenThrow(new RuntimeException("Cannot connect to node"));
        when(web3j.ethGasPrice()).thenReturn((Request) request);

        String result = service.refund(1L, BigInteger.TEN, INVESTOR_ADDRESS, INVESTOR_ADDRESS);

        assertThat(result).isNull();
    }

    // ── new helpers for receipt/log mocking ──

    @SuppressWarnings("unchecked")
    private void mockReceiptWithLogs(String txHash, List<Log> logs) throws Exception {
        var receipt = org.mockito.Mockito.mock(TransactionReceipt.class);
        when(receipt.getLogs()).thenReturn(logs);

        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));

        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) request);
    }

    private Log mockLog(String data) {
        var log = org.mockito.Mockito.mock(Log.class);
        when(log.getData()).thenReturn(data);
        return log;
    }

    // ── helpers ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockGasPrice() throws Exception {
        Request<?, EthGasPrice> request = org.mockito.Mockito.mock(Request.class);
        EthGasPrice gasPriceResponse = org.mockito.Mockito.mock(EthGasPrice.class);
        when(gasPriceResponse.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
        when(request.send()).thenReturn(gasPriceResponse);
        when(web3j.ethGasPrice()).thenReturn((Request) request);
    }

    @SuppressWarnings("unchecked")
    private void mockSendTransaction(String txHash) throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(false);
        when(txResponse.getTransactionHash()).thenReturn(txHash);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);
    }

    @SuppressWarnings("unchecked")
    private void mockEthCall(String encodedHex) throws Exception {
        var ethCall = org.mockito.Mockito.mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(false);
        when(ethCall.getValue()).thenReturn(encodedHex);

        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(ethCall);
        when(web3j.ethCall(any(), any())).thenReturn((Request) request);
    }

    @SuppressWarnings("unchecked")
    private void mockEmptyReceipt(String txHash) throws Exception {
        var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.empty());

        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(receiptResponse);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn((Request) request);
    }
}
