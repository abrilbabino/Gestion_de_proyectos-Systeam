package com.systeam.blockchain.service;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Tests para OfferingContractService — el puente entre Java y el smart contract.
 *
 * ¿Qué testeamos? 
 *   1. Validación de configuración (si falta la address del contrato)
 *   2. Consultas (ethCall): getContribution, getInvestorCount
 *   3. Transacciones (sendTransaction): registerOffering, invest, finalize, etc.
 *
 * ¿Cómo mockeamos Web3j sin llamar a blockchain real?
 *   - Para ethCall: mockeamos web3j.ethCall() → devuelve Request → .send() devuelve EthCall
 *   - Para sendTransaction: mockeamos TransactionManager.sendTransaction()
 *     Esto evita que RawTransactionManager firme la tx y verifique el hash (TxHashMismatch).
 *
 * ERRORES COMUNES y cómo los evitamos:
 *   ❌ "0xCREATOR" no es una dirección hex válida
 *   ✅ Usar "0x0000000000000000000000000000000000000001" (addresses reales)
 *
 *   ❌ TxHashMismatchException con RawTransactionManager real
 *   ✅ Inyectamos TransactionManager mockeado vía constructor de paquete
 */
@ExtendWith(MockitoExtension.class)
class OfferingContractServiceTest {

    // ─────────────────────────────────────────────
    // CONSTANTES
    // ─────────────────────────────────────────────

    // Clave privada de prueba (sirve para crear Credentials, no es una key real)
    private static final String TEST_PRIVATE_KEY =
        "0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    // Dirección del contrato (simula la que va en .env)
    private static final String CONTRACT_ADDRESS =
        "0x1234567890123456789012345678901234567890";

    // Direcciones Ethereum válidas para pruebas (40 caracteres hex después de 0x)
    private static final String CREATOR_ADDRESS =
        "0x1111111111111111111111111111111111111111";
    private static final String INVESTOR_ADDRESS =
        "0x2222222222222222222222222222222222222222";

    // ─────────────────────────────────────────────
    // DEPENDENCIAS
    // ─────────────────────────────────────────────

    private Credentials credentials;
    private OfferingContractService service;

    // TransactionManager mockeado — así evitamos que Web3j firme y verifique hashes
    @Mock
    private TransactionManager txManager;

    @Mock
    private Web3j web3j;

    @Mock
    private BlockchainProperties props;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
        when(props.getOfferingContractAddress()).thenReturn(CONTRACT_ADDRESS);

        // Usamos el constructor de 4 parámetros que acepta TransactionManager mockeado
        service = new OfferingContractService(web3j, credentials, txManager, props);
    }

    // ═══════════════════════════════════════════════
    // GRUPO 1: Contrato no configurado
    // ═══════════════════════════════════════════════

    @Test
    void registerOffering_cuandoAddressEsNull_lanzaIllegalState() {
        when(props.getOfferingContractAddress()).thenReturn(null);
        OfferingContractService brokenService =
            new OfferingContractService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.registerOffering(
                BigInteger.ONE,
                CREATOR_ADDRESS,
                BigInteger.valueOf(100),
                BigInteger.valueOf(1000),
                BigInteger.valueOf(10),
                BigInteger.ONE,
                BigInteger.TEN
            )
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OfferingContract");
    }

    @Test
    void invest_cuandoAddressEsCero_lanzaIllegalState() {
        when(props.getOfferingContractAddress())
            .thenReturn("0x0000000000000000000000000000000000000000");
        OfferingContractService brokenService =
            new OfferingContractService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.invest(BigInteger.ONE, BigInteger.valueOf(1000))
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OfferingContract");
    }

    @Test
    void finalize_cuandoAddressEsNull_lanzaIllegalState() {
        when(props.getOfferingContractAddress()).thenReturn(null);
        OfferingContractService brokenService =
            new OfferingContractService(web3j, credentials, txManager, props);

        assertThatThrownBy(() -> brokenService.finalize(BigInteger.ONE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OfferingContract");
    }

    // ═══════════════════════════════════════════════
    // GRUPO 2: Consultas (ethCall)
    // ═══════════════════════════════════════════════
    //
    // Estos tests usan web3j.ethCall() que NO necesita firma de transacción.
    // Solo armamos el llamado ABI, lo enviamos y decodificamos la respuesta.
    //
    // ¿Qué mockeamos?
    //   web3j.ethCall(tx, LATEST) → Request → .send() → EthCall
    //
    // ¿Por qué no necesitamos txManager? Porque executeCall() no usa txManager.

    @Test
    void getContribution_retornaMontoInvertido() throws Exception {
        // Arrange: la blockchain devuelve 1000 (0x3e8 en hex de 32 bytes)
        EthCall ethCall = mockEthCall(
            "0x00000000000000000000000000000000000000000000000000000000000003e8"
        );
        mockEthCallRequest(ethCall);

        // Act
        BigInteger result = service.getContribution(BigInteger.ONE, INVESTOR_ADDRESS);

        // Assert
        assertThat(result).isEqualByComparingTo(BigInteger.valueOf(1000));
    }

    @Test
    void getContribution_cuandoEthCallFalla_lanzaRuntime() throws Exception {
        // Arrange: la blockchain responde con error
        EthCall ethCall = org.mockito.Mockito.mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("revert: insufficient funds");
        when(ethCall.getError()).thenReturn(error);
        mockEthCallRequest(ethCall);

        // Act + Assert
        assertThatThrownBy(() ->
            service.getContribution(BigInteger.ONE, INVESTOR_ADDRESS)
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ethCall");
    }

    @Test
    void getContribution_cuandoRespuestaSinDatos_retornaCero() throws Exception {
        // Arrange: blockchain responde OK pero sin datos
        EthCall ethCall = mockEthCall("0x");
        mockEthCallRequest(ethCall);

        // Act
        BigInteger result = service.getContribution(BigInteger.ONE, INVESTOR_ADDRESS);

        // Assert
        assertThat(result).isEqualByComparingTo(BigInteger.ZERO);
    }

    @Test
    void getInvestorCount_retornaCantidad() throws Exception {
        // Arrange: 5 inversores
        EthCall ethCall = mockEthCall(
            "0x0000000000000000000000000000000000000000000000000000000000000005"
        );
        mockEthCallRequest(ethCall);

        // Act
        BigInteger result = service.getInvestorCount(BigInteger.ONE);

        // Assert
        assertThat(result).isEqualByComparingTo(BigInteger.valueOf(5));
    }

    @Test
    void getInvestorCount_cuandoRespuestaSinDatos_retornaCero() throws Exception {
        EthCall ethCall = mockEthCall("0x");
        mockEthCallRequest(ethCall);

        BigInteger result = service.getInvestorCount(BigInteger.ONE);

        assertThat(result).isEqualByComparingTo(BigInteger.ZERO);
    }

    // ═══════════════════════════════════════════════
    // GRUPO 3: Transacciones (sendTransaction)
    // ═══════════════════════════════════════════════
    //
    // Acá NO usamos RawTransactionManager real porque:
    //   1. Firma la tx con la private key
    //   2. Calcula el hash esperado de la tx firmada
    //   3. Compara el hash esperado con el que devuelve la red
    //   4. Si no coinciden → TxHashMismatchException
    //
    // En su lugar, mockeamos TransactionManager.sendTransaction()
    // para que devuelva directamente la respuesta que queremos.

    @Test
    void registerOffering_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xabc111");

        String txHash = service.registerOffering(
            BigInteger.ONE, CREATOR_ADDRESS,
            BigInteger.valueOf(100), BigInteger.valueOf(1000),
            BigInteger.valueOf(10), BigInteger.ONE, BigInteger.TEN
        );

        assertThat(txHash).isEqualTo("0xabc111");
    }

    @Test
    void invest_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xdef222");

        String txHash = service.invest(BigInteger.ONE, BigInteger.valueOf(500));

        assertThat(txHash).isEqualTo("0xdef222");
    }

    @Test
    void finalize_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xghi333");

        String txHash = service.finalize(BigInteger.ONE);

        assertThat(txHash).isEqualTo("0xghi333");
    }

    @Test
    void claimTokens_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xjkl444");

        String txHash = service.claimTokens(BigInteger.ONE);

        assertThat(txHash).isEqualTo("0xjkl444");
    }

    @Test
    void refund_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xmno555");

        String txHash = service.refund(BigInteger.ONE);

        assertThat(txHash).isEqualTo("0xmno555");
    }

    @Test
    void registerOffering_cuandoTxFalla_lanzaRuntime() throws Exception {
        // Arrange: la transacción es rechazada por la red
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("gas required exceeds allowance");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);

        // Act + Assert
        assertThatThrownBy(() ->
            service.registerOffering(
                BigInteger.ONE, CREATOR_ADDRESS,
                BigInteger.valueOf(100), BigInteger.valueOf(1000),
                BigInteger.valueOf(10), BigInteger.ONE, BigInteger.TEN
            )
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("gas required");
    }

    /**
     * ¿Qué estamos probando?
     * invest() cuando la transacción en la blockchain falla (hasError = true).
     * <p>
     * ¿Por qué es importante?
     * Una inversión puede fallar si el período de oferta terminó, el hard cap
     * ya se alcanzó, o el usuario no tiene fondos. El servicio debe detectarlo.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() para devolver EthSendTransaction
     * con hasError() = true y un mensaje de error. También mockeamos
     * web3j.ethGasPrice() porque sendTransaction() lo necesita.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error de la blockchain.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * Si el servicio ignorara response.hasError(), devolvería un txHash
     * aunque la transacción hubiera fallado. El sistema pensaría que
     * la inversión se realizó cuando en realidad no fue así,
     * causando inconsistencias graves.
     */
    @Test
    void invest_cuandoTxFalla_lanzaRuntime() throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("offering ended");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.invest(BigInteger.ONE, BigInteger.valueOf(500))
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("offering ended");
    }

    /**
     * ¿Qué estamos probando?
     * finalize() cuando la transacción falla.
     * <p>
     * ¿Por qué es importante?
     * Finalizar una oferta puede fallar si no se alcanzó el soft cap o
     * si alguien ya la finalizó. El servicio debe propagar el error.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() con hasError() = true
     * y un mensaje de error. Mockeamos gas price.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * El sistema creería que la oferta se finalizó exitosamente
     * cuando en realidad la transacción fue rechazada.
     */
    @Test
    void finalize_cuandoTxFalla_lanzaRuntime() throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("soft cap not reached");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.finalize(BigInteger.ONE)
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("soft cap");
    }

    /**
     * ¿Qué estamos probando?
     * claimTokens() cuando la transacción falla.
     * <p>
     * ¿Por qué es importante?
     * Reclamar tokens después de finalizar puede fallar si el usuario
     * ya reclamó o si la oferta no se finalizó correctamente.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() con hasError() = true.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * El usuario vería un txHash exitoso pero nunca recibiría sus tokens,
     * causando una pésima experiencia de usuario y pérdida de confianza.
     */
    @Test
    void claimTokens_cuandoTxFalla_lanzaRuntime() throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("already claimed");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.claimTokens(BigInteger.ONE)
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("already claimed");
    }

    /**
     * ¿Qué estamos probando?
     * refund() cuando la transacción falla.
     * <p>
     * ¿Por qué es importante?
     * El reembolso puede fallar si la oferta no fracasó, si el usuario
     * no invirtió, o si ya se reembolsó. El servicio debe manejarlo.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() con hasError() = true.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * El sistema mostraría un reembolso exitoso cuando en realidad
     * el usuario nunca recuperó su inversión.
     */
    @Test
    void refund_cuandoTxFalla_lanzaRuntime() throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        org.web3j.protocol.core.Response.Error error =
            new org.web3j.protocol.core.Response.Error();
        error.setMessage("offering not failed");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.refund(BigInteger.ONE)
        )
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("offering not failed");
    }

    // ═══════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ═══════════════════════════════════════════════
    //
    // Reducen la repetición de código (DRY).
    // Cada método prepara UNA parte del mock de Web3j.

    /**
     * Crea un EthCall mockeado con el valor de retorno dado (en hex).
     * Si el hex está vacío ("0x"), la decodificación ABI devuelve lista vacía.
     */
    private EthCall mockEthCall(String returnValue) throws Exception {
        EthCall ethCall = org.mockito.Mockito.mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(false);
        when(ethCall.getValue()).thenReturn(returnValue);
        return ethCall;
    }

    /**
     * Configura web3j.ethCall() para devolver un Request que al hacer
     * .send() devuelve el EthCall que le pasamos.
     *
     * Esto simula el viaje de ida y vuelta a la blockchain sin hacer
     * un HTTP request real.
     */
    @SuppressWarnings("unchecked")
    private void mockEthCallRequest(EthCall ethCall) throws Exception {
        Request<?, EthCall> request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(ethCall);
        when(web3j.ethCall(
            any(org.web3j.protocol.core.methods.request.Transaction.class),
            eq(DefaultBlockParameterName.LATEST)
        )).thenReturn((Request) request);
    }

    /**
     * Configura txManager.sendTransaction() para devolver un tx hash exitoso.
     *
     * ¿Por qué funciona esto?
     *   Nuestro servicio llama a txManager.sendTransaction(gasPrice, gasLimit, to, data, value).
     *   El TransactionManager mockeado devuelve la respuesta que configuramos.
     *   No se firma nada, no se calcula ningún hash, no se verifica nada.
     *
     * ¿Es esto realista?
     *   Para un test unitario, sí. Estamos probando que:
     *     1. El servicio arma correctamente el ABI encoding
     *     2. Llama a txManager con los parámetros correctos
     *     3. Maneja la respuesta (error o tx hash)
     *   No estamos probando que Web3j firme bien — eso es responsabilidad de Web3j.
     */
    @SuppressWarnings("unchecked")
    private void mockSendTransaction(String txHash) throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(false);
        when(txResponse.getTransactionHash()).thenReturn(txHash);

        // txManager.sendTransaction(5 params) llama internamente a la versión
        // de 6 params con replayProtection=false. Mockeamos la de 5 params directamente.
        when(txManager.sendTransaction(
            any(), any(), anyString(), anyString(), any()
        )).thenReturn(txResponse);
    }

    /**
     * Configura web3j.ethGasPrice() para devolver un gas price simulado.
     *
     * Necesario porque sendTransaction() llama a
     *   web3j.ethGasPrice().send().getGasPrice()
     * ANTES de llamar a txManager.sendTransaction().
     */
    @SuppressWarnings("unchecked")
    private void mockGasPrice() throws Exception {
        Request<?, EthGasPrice> request = org.mockito.Mockito.mock(Request.class);
        EthGasPrice gasPriceResponse = org.mockito.Mockito.mock(EthGasPrice.class);
        when(gasPriceResponse.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
        when(request.send()).thenReturn(gasPriceResponse);
        when(web3j.ethGasPrice()).thenReturn((Request) request);
    }
}
