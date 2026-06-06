package com.systeam.blockchain.service;

import java.math.BigInteger;

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
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Tests para IdeaMarketplaceService — el puente entre Java y el smart contract
 * IdeaMarketplace en la blockchain.
 * <p>
 * ¿Qué hace IdeaMarketplaceService?
 * Permite listar tokens en venta (listTokens), comprar tokens (buyTokens),
 * cancelar una listing (cancelListing) y cobrar comisiones acumuladas (collectFees).
 * <p>
 * ¿Cómo testeamos sin una blockchain real?
 * Usamos Mockito para simular Web3j, TransactionManager y BlockchainProperties.
 * - Para todas las operaciones: mockeamos txManager.sendTransaction()
 *   que devuelve un EthSendTransaction simulado.
 * - No usamos RawTransactionManager real para evitar TxHashMismatchException.
 * <p>
 * Errores comunes que estos tests previenen:
 * - Llamar a la blockchain sin configurar la dirección del marketplace
 * - Usar dirección zero (0x0000...0000) como dirección del contrato
 * - No detectar errores en las transacciones (hasError = true)
 * - Asumir que todas las transacciones tienen éxito
 */
@ExtendWith(MockitoExtension.class)
class IdeaMarketplaceServiceTest {

    private static final String TEST_PRIVATE_KEY =
        "0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static final String SUBTOKEN_ADDRESS =
        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String MARKETPLACE_ADDRESS =
        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private Credentials credentials;
    private IdeaMarketplaceService service;

    @Mock
    private TransactionManager txManager;

    @Mock
    private Web3j web3j;

    @Mock
    private BlockchainProperties props;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
        when(props.getIdeaMarketplaceAddress()).thenReturn(MARKETPLACE_ADDRESS);

        service = new IdeaMarketplaceService(web3j, credentials, txManager, props);
    }

    @Test
    void listTokens_cuandoAddressEsNull_lanzaIllegalState() {
        when(props.getIdeaMarketplaceAddress()).thenReturn(null);
        var brokenService = new IdeaMarketplaceService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.listTokens(SUBTOKEN_ADDRESS, BigInteger.TEN, BigInteger.valueOf(100)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IdeaMarketplace");
    }

    @Test
    void buyTokens_cuandoAddressEsCero_lanzaIllegalState() {
        when(props.getIdeaMarketplaceAddress())
            .thenReturn("0x0000000000000000000000000000000000000000");
        var brokenService = new IdeaMarketplaceService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.buyTokens(BigInteger.ONE, BigInteger.TEN))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IdeaMarketplace");
    }

    @Test
    void listTokens_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xlist001");

        String txHash = service.listTokens(SUBTOKEN_ADDRESS, BigInteger.TEN, BigInteger.valueOf(100));

        assertThat(txHash).isEqualTo("0xlist001");
    }

    @Test
    void buyTokens_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xbuy002");

        String txHash = service.buyTokens(BigInteger.ONE, BigInteger.valueOf(50));

        assertThat(txHash).isEqualTo("0xbuy002");
    }

    @Test
    void cancelListing_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xcancel003");

        String txHash = service.cancelListing(BigInteger.ONE);

        assertThat(txHash).isEqualTo("0xcancel003");
    }

    @Test
    void collectFees_retornaTxHash() throws Exception {
        mockGasPrice();
        mockSendTransaction("0xfees004");

        String txHash = service.collectFees();

        assertThat(txHash).isEqualTo("0xfees004");
    }

    @Test
    void listTokens_cuandoTxFalla_lanzaRuntime() throws Exception {
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("gas required exceeds allowance");
        when(txResponse.getError()).thenReturn(error);

        mockGasPrice();
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.listTokens(SUBTOKEN_ADDRESS, BigInteger.TEN, BigInteger.valueOf(100)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("gas required");
    }

    /**
     * ¿Qué estamos probando?
     * buyTokens() cuando la transacción en la blockchain falla (hasError = true).
     * <p>
     * ¿Por qué es importante?
     * Comprar tokens puede fallar si la listing ya no existe, el vendedor
     * la canceló, o no hay suficientes tokens. El servicio debe detectarlo.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos props.getIdeaMarketplaceAddress() con dirección válida,
     * web3j.ethGasPrice() para el gas price,
     * y txManager.sendTransaction() con hasError() = true.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error de la blockchain.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * Si el servicio ignorara response.hasError(), devolvería un txHash
     * indicando que la compra fue exitosa, pero en realidad los tokens
     * nunca se transfirieron. Esto causaría una grave inconsistencia.
     */
    @Test
    void buyTokens_cuandoTxFalla_lanzaRuntime() throws Exception {
        mockGasPrice();
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("listing not active");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.buyTokens(BigInteger.ONE, BigInteger.valueOf(50)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("listing not active");
    }

    /**
     * ¿Qué estamos probando?
     * cancelListing() cuando la transacción falla.
     * <p>
     * ¿Por qué es importante?
     * Cancelar una listing puede fallar si no eres el dueño o
     * si ya fue cancelada/completada.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() con hasError() = true.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * El usuario creería que canceló su listing cuando en realidad
     * sigue activa, y otros podrían seguir comprando sus tokens.
     */
    @Test
    void cancelListing_cuandoTxFalla_lanzaRuntime() throws Exception {
        mockGasPrice();
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("not the listing owner");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.cancelListing(BigInteger.ONE))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not the listing owner");
    }

    /**
     * ¿Qué estamos probando?
     * collectFees() cuando la transacción falla.
     * <p>
     * ¿Por qué es importante?
     * Cobrar comisiones puede fallar si no hay comisiones acumuladas
     * o si el llamante no es el owner del contrato.
     * <p>
     * ¿Cómo funciona el setup?
     * Mockeamos txManager.sendTransaction() con hasError() = true.
     * <p>
     * ¿Cuál es el resultado esperado?
     * RuntimeException con el mensaje de error.
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * El sistema pensaría que las comisiones se cobraron cuando
     * en realidad la transacción fue rechazada, perdiendo el rastro
     * de las comisiones acumuladas en la blockchain.
     */
    @Test
    void collectFees_cuandoTxFalla_lanzaRuntime() throws Exception {
        mockGasPrice();
        EthSendTransaction txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
        when(txResponse.hasError()).thenReturn(true);
        var error = new org.web3j.protocol.core.Response.Error();
        error.setMessage("no fees to collect");
        when(txResponse.getError()).thenReturn(error);
        when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
            .thenReturn(txResponse);

        assertThatThrownBy(() ->
            service.collectFees())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("no fees to collect");
    }

    /**
     * ¿Qué estamos probando?
     * cancelListing() cuando la dirección del marketplace es la dirección zero.
     * <p>
     * ¿Por qué es importante?
     * La dirección zero no es un contrato válido. Si no se filtra,
     * se intentaría cancelar una listing en una dirección inválida.
     * <p>
     * ¿Cómo funciona el setup?
     * Creamos un servicio con props.getIdeaMarketplaceAddress() = ZERO_ADDRESS.
     * assertConfigured() lanza IllegalStateException.
     * <p>
     * ¿Cuál es el resultado esperado?
     * IllegalStateException con mensaje "IdeaMarketplace no configurado".
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * Se enviaría una transacción de cancelación a 0x0000...0000,
     * desperdiciando gas y quedando la listing activa.
     */
    @Test
    void cancelListing_cuandoAddressEsCero_lanzaIllegalState() {
        when(props.getIdeaMarketplaceAddress())
            .thenReturn(ZERO_ADDRESS);
        var brokenService = new IdeaMarketplaceService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.cancelListing(BigInteger.ONE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IdeaMarketplace");
    }

    /**
     * ¿Qué estamos probando?
     * collectFees() cuando la dirección del marketplace es la dirección zero.
     * <p>
     * ¿Por qué es importante?
     * Cobrar comisiones en la dirección zero no tiene sentido y fallaría.
     * <p>
     * ¿Cómo funciona el setup?
     * Creamos un servicio con props.getIdeaMarketplaceAddress() = ZERO_ADDRESS.
     * assertConfigured() lanza IllegalStateException.
     * <p>
     * ¿Cuál es el resultado esperado?
     * IllegalStateException con mensaje "IdeaMarketplace no configurado".
     * <p>
     * ¿Qué pasaría si el código estuviera mal?
     * Se intentaría cobrar comisiones de 0x0000...0000, transacción
     * fallida y gas desperdiciado.
     */
    @Test
    void collectFees_cuandoAddressEsCero_lanzaIllegalState() {
        when(props.getIdeaMarketplaceAddress())
            .thenReturn(ZERO_ADDRESS);
        var brokenService = new IdeaMarketplaceService(web3j, credentials, txManager, props);

        assertThatThrownBy(() ->
            brokenService.collectFees())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IdeaMarketplace");
    }

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
}
