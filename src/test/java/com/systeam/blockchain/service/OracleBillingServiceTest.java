package com.systeam.blockchain.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Credentials;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

/**
 * Pruebas unitarias para {@link OracleBillingService}.
 * <p>
 * Este servicio envia reportes de facturacion a un contrato OracleBilling
 * en la blockchain y guarda el resultado en la base de datos.
 * Todas las interacciones con Web3j y JdbcTemplate estan mockeadas.
 * </p>
 *
 * <p>
 * Temas cubiertos:
 * <ul>
 *   <li>Flujo completo exitoso</li>
 *   <li>Validacion de direccion (null, blank, cero)</li>
 *   <li>Fallos en transacciones (sendTransaction con error)</li>
 *   <li>Timeouts de receipt (transaction no minada)</li>
 *   <li>Receipt revertido (status &quot;0x0&quot;)</li>
 *   <li>Desbordamiento de decimales en monto</li>
 *   <li>Excepciones de RPC envueltas en RuntimeException</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OracleBillingServiceTest {

    private static final String BILLING_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TX_HASH = "0xtx123";
    private static final Long PROYECTO_ID = 1L;
    private static final BigDecimal MONTO = BigDecimal.valueOf(100.50);
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;
    @Mock
    private BlockchainProperties props;
    @Mock
    private JdbcTemplate jdbc;

    private OracleBillingService service;

    @BeforeEach
    void setUp() {
        service = new OracleBillingService(web3j, credentials, txManager, props, jdbc);
    }

    @Nested
    @DisplayName("submitBillingReport")
    class SubmitBillingReport {

        @SuppressWarnings("unchecked")
        @Test
        void exito_flujoCompleto() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn(TX_HASH);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);
            var receipt = new TransactionReceipt();
            receipt.setStatus("0x1");
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) receiptRequest);

            Map<String, Object> result = service.submitBillingReport(PROYECTO_ID, MONTO);

            assertThat(result)
                .containsEntry("proyectoId", PROYECTO_ID)
                .containsEntry("validado", true)
                .containsKey("txHash");
        }

        @Test
        void noConfigurado_lanzaIllegalState() {
            when(props.getOracleBillingAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OracleBilling");
        }

        @SuppressWarnings("unchecked")
        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("billing failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("billing failed");
        }

        @SuppressWarnings("unchecked")
        @Test
        void receiptVacio_lanzaTimeout() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn(TX_HASH);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.empty());
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) receiptRequest);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("minada");
        }

        @SuppressWarnings("unchecked")
        @Test
        void receiptRevertido_lanzaRuntime() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn(TX_HASH);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);
            var receipt = new TransactionReceipt();
            receipt.setStatus("0x0");
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) receiptRequest);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revertida");
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * OracleBilling es un String vacio (""), el metodo lanza IllegalStateException.
         * �Por que es importante? La validacion en la linea 58 usa isBlank().
         * Sin esta prueba, si alguien cambia la validacion a solo null,
         * las direcciones vacias pasarian sin ser detectadas.
         * �Como funciona el setup? Mockeamos props.getOracleBillingAddress() para que
         * retorne "" (un String vacio).
         * �Cual es el resultado esperado? IllegalStateException con mensaje "OracleBilling".
         * �Que pasaria si el codigo estuviera mal? Una direccion vacia llegaria a la
         * blockchain y causaria errores dificiles de rastrear.
         */
        @Test
        void submitBillingReport_cuandoAddressEsBlank_lanzaIllegalState() {
            when(props.getOracleBillingAddress()).thenReturn("");

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OracleBilling");
        }

        /**
         * �Que estamos probando? Verificamos que cuando la direccion del contrato
         * OracleBilling es la direccion cero (0x000...000), el metodo lanza
         * IllegalStateException.
         * �Por que es importante? La direccion cero no es un contrato valido.
         * Enviar una transaccion a la direccion cero la perderia para siempre.
         * �Como funciona el setup? Mockeamos props.getOracleBillingAddress() para que
         * retorne ZERO_ADDRESS.
         * �Cual es el resultado esperado? IllegalStateException con mensaje "OracleBilling".
         * �Que pasaria si el codigo estuviera mal? Los fondos se enviarian a la
         * direccion cero sin posibilidad de recuperacion.
         */
        @Test
        void submitBillingReport_cuandoAddressEsCero_lanzaIllegalState() {
            when(props.getOracleBillingAddress()).thenReturn(ZERO_ADDRESS);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OracleBilling");
        }

        /**
         * �Que estamos probando? Verificamos que si el BigDecimal monto tiene mas de
         * 18 decimales, el metodo lanza RuntimeException por la llamada a
         * toBigIntegerExact().
         * �Por que es importante? toBigIntegerExact() lanza ArithmeticException si el
         * valor no es entero. Sin esta prueba, montos con decimales excesivos podrian
         * causar errores inesperados no controlados.
         * �Como funciona el setup? Pasamos un BigDecimal con 19 decimales
         * ("0.0000000000000000001"). Al multiplicarlo por 10^18 da 0.0000000001,
         * que no es entero.
         * �Cual es el resultado esperado? RuntimeException cuyo cause es
         * ArithmeticException.
         * �Que pasaria si el codigo estuviera mal? Un BigDecimal con muchos decimales
         * pasaria sin ser detectado y causaria un error aritmetico.
         */
        @Test
        void submitBillingReport_cuandoMontoTieneDemasDecimales_lanzaRuntime() {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            BigDecimal montoCon19Decimales = new BigDecimal("0.0000000000000000001");

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, montoCon19Decimales))
                .isInstanceOf(ArithmeticException.class);
        }

        /**
         * �Que estamos probando? Verificamos que si web3j.ethGasPrice().send() lanza
         * una IOException, el metodo la envuelve en un RuntimeException.
         * �Por que es importante? El bloque catch(Exception) en la linea 118 captura
         * cualquier excepcion que no sea RuntimeException y la envuelve. Sin esta
         * prueba, una falla de RPC pasaria desapercibida en las pruebas.
         * �Como funciona el setup? Mockeamos web3j.ethGasPrice() para que el Request
         * lance IOException al llamar send().
         * �Cual es el resultado esperado? RuntimeException con mensaje "Error al
         * enviar billing report".
         * �Que pasaria si el codigo estuviera mal? Si el catch no existiera, la
         * IOException se propagaria sin control y el usuario recibiria un error
         * tecnico en lugar de un mensaje amigable.
         */
        @SuppressWarnings("unchecked")
        @Test
        void submitBillingReport_cuandoEthGasPriceLanzaExcepcion_lanzaRuntime() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            var gpRequest = org.mockito.Mockito.mock(Request.class);
            when(gpRequest.send()).thenThrow(new IOException("Connection refused"));
            when(web3j.ethGasPrice()).thenReturn((Request) gpRequest);

            assertThatThrownBy(() -> service.submitBillingReport(PROYECTO_ID, MONTO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al enviar billing report");
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
}
