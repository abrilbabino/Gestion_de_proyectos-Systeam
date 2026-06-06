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
 * Pruebas unitarias para {@link IdeaGovernanceService}.
 * <p>
 * Este servicio gestiona la gobernanza de ideas en la blockchain: creaci&oacute;n de
 * propuestas, votaci&oacute;n, ejecuci&oacute;n y consultas. Todas las pruebas utilizan mocks
 * para simular la interacci&oacute;n con la red Ethereum.
 * </p>
 *
 * <h2>Estructura de las pruebas</h2>
 * <ul>
 *   <li><strong>createProposal:</strong> Prueba la creaci&oacute;n de propuestas.</li>
 *   <li><strong>vote:</strong> Verifica la votaci&oacute;n a favor y en contra.</li>
 *   <li><strong>executeProposal:</strong> Prueba la ejecuci&oacute;n de propuestas
 *       aprobadas.</li>
 *   <li><strong>getProposalCount:</strong> Verifica la consulta del contador
 *       de propuestas.</li>
 *   <li><strong>getProposal:</strong> Prueba la obtenci&oacute;n de datos de una
 *       propuesta espec&iacute;fica.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IdeaGovernanceServiceTest {

    private static final String GOVERNANCE_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TX_HASH = "0xtx123";
    private static final String BACKEND_ADDR = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final BigInteger PROPOSAL_ID = BigInteger.ONE;

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;
    @Mock
    private BlockchainProperties props;

    private IdeaGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new IdeaGovernanceService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("createProposal")
    class CreateProposal {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.createProposal("test", 1, new byte[]{0x01, 0x02});

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void noConfigurado_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress()).thenReturn(null);

            assertThatThrownBy(() -> service.createProposal("test", 1, new byte[]{0x01}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        /**
         * ¿Qué estamos probando? Que createProposal lanza IllegalStateException cuando
         * la direcci&oacute;n de governance est&aacute; configurada como la direcci&oacute;n cero (0x000...0).
         * ¿Por qué es importante? La direcci&oacute;n cero indica que el contrato no ha sido
         * desplegado/configurado. Si no se valida, se intentar&iacute;a enviar una transacci&oacute;n
         * a la direcci&oacute;n quemada, perdiendo gas.
         * ¿Cómo funciona el setup? Mockeamos props.getIdeaGovernanceAddress() para
         * retornar la direcci&oacute;n cero. assertConfigured detecta que es cero y lanza.
         * ¿Cuál es el resultado esperado? IllegalStateException con "IdeaGovernance".
         * ¿Qué pasaría si el código estuviera mal? Si assertConfigured solo validara null
         * pero no la direcci&oacute;n cero, se perder&iacute;a gas al enviar tx a direcci&oacute;n quemada.
         */
        @Test
        void addressEsCero_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress())
                .thenReturn("0x0000000000000000000000000000000000000000");

            assertThatThrownBy(() -> service.createProposal("test", 1, new byte[]{0x01}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("create failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.createProposal("test", 1, new byte[]{0x01}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("create failed");
        }
    }

    @Nested
    @DisplayName("vote")
    class Vote {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.vote(PROPOSAL_ID, true);

            assertThat(result).isEqualTo(TX_HASH);
        }

        @Test
        void votaEnContra_enviaTx() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.vote(PROPOSAL_ID, false);

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * ¿Qué estamos probando? Que vote lanza IllegalStateException cuando la
         * direcci&oacute;n de governance es la direcci&oacute;n cero.
         * ¿Por qué es importante? Misma raz&oacute;n que en createProposal: evitar enviar
         * transacciones a la direcci&oacute;n quemada.
         * ¿Cómo funciona el setup? Mockeamos props.getIdeaGovernanceAddress() con la
         * direcci&oacute;n cero. assertConfigured la detecta y lanza.
         * ¿Cuál es el resultado esperado? IllegalStateException con "IdeaGovernance".
         * ¿Qué pasaría si el código estuviera mal? Se enviar&iacute;a gas a 0x000...0
         * sin obtener ning&uacute;n resultado.
         */
        @Test
        void addressEsCero_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress())
                .thenReturn("0x0000000000000000000000000000000000000000");

            assertThatThrownBy(() -> service.vote(PROPOSAL_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        /**
         * ¿Qué estamos probando? Que vote lanza RuntimeException cuando la transacci&oacute;n
         * falla en la red (sendTransaction retorna hasError=true).
         * ¿Por qué es importante? Si la transacci&oacute;n falla (por ejemplo, el usuario no
         * tiene saldo o la propuesta ya expir&oacute;), debemos propagar el error.
         * ¿Cómo funciona el setup? Mockeamos txManager.sendTransaction para retornar
         * una respuesta con error. Es el mismo patr&oacute;n que en createProposal.
         * ¿Cuál es el resultado esperado? RuntimeException con "vote failed".
         * ¿Qué pasaría si el código estuviera mal? Si no verificara hasError(),
         * retornar&iacute;a un txHash vac&iacute;o o null y el llamador no sabr&iacute;a que fall&oacute;.
         */
        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("vote failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.vote(PROPOSAL_ID, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("vote failed");
        }
    }

    @Nested
    @DisplayName("executeProposal")
    class ExecuteProposal {

        @Test
        void exito_enviaTx() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            mockSendTransaction(TX_HASH);

            String result = service.executeProposal(PROPOSAL_ID);

            assertThat(result).isEqualTo(TX_HASH);
        }

        /**
         * ¿Qué estamos probando? Que executeProposal lanza IllegalStateException cuando
         * la direcci&oacute;n de governance es la direcci&oacute;n cero.
         * ¿Por qué es importante? Igual que en los m&eacute;todos anteriores: prevenir el
         * env&iacute;o de transacciones a la direcci&oacute;n quemada.
         * ¿Cómo funciona el setup? Mockeamos props.getIdeaGovernanceAddress() con la
         * direcci&oacute;n cero. assertConfigured la detecta y lanza.
         * ¿Cuál es el resultado esperado? IllegalStateException con "IdeaGovernance".
         * ¿Qué pasaría si el código estuviera mal? Se ejecutar&iacute;a una transacci&oacute;n
         * de executeProposal contra la direcci&oacute;n cero, perdiendo gas.
         */
        @Test
        void addressEsCero_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress())
                .thenReturn("0x0000000000000000000000000000000000000000");

            assertThatThrownBy(() -> service.executeProposal(PROPOSAL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        /**
         * ¿Qué estamos probando? Que executeProposal lanza RuntimeException cuando
         * la transacci&oacute;n falla en la red.
         * ¿Por qué es importante? Si la ejecuci&oacute;n falla (por ejemplo, la propuesta no
         * tiene suficientes votos o ya fue ejecutada), debemos informar el error.
         * ¿Cómo funciona el setup? Mockeamos txManager.sendTransaction con hasError=true
         * y un mensaje de error.
         * ¿Cuál es el resultado esperado? RuntimeException con "execute failed".
         * ¿Qué pasaría si el código estuviera mal? Retornar&iacute;a un txHash aunque la
         * transacci&oacute;n no se hubiera enviado realmente.
         */
        @Test
        void txFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            mockGasPrice();
            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("execute failed");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.executeProposal(PROPOSAL_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("execute failed");
        }
    }

    @Nested
    @DisplayName("getProposalCount")
    class GetProposalCount {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaContador() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000005");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getProposalCount();

            assertThat(result).isEqualTo(BigInteger.valueOf(5));
        }

        @SuppressWarnings("unchecked")
        @Test
        void resultadoVacio_retornaCero() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            BigInteger result = service.getProposalCount();

            assertThat(result).isEqualTo(BigInteger.ZERO);
        }

        /**
         * ¿Qué estamos probando? Que getProposalCount lanza IllegalStateException cuando
         * la direcci&oacute;n de governance es la direcci&oacute;n cero.
         * ¿Por qué es importante? Incluso las consultas (lecturas) deben validar la
         * configuraci&oacute;n. Si la direcci&oacute;n es cero, no tiene sentido consultar.
         * ¿Cómo funciona el setup? Mockeamos props.getIdeaGovernanceAddress() con la
         * direcci&oacute;n cero.
         * ¿Cuál es el resultado esperado? IllegalStateException con "IdeaGovernance".
         * ¿Qué pasaría si el código estuviera mal? Se har&iacute;a una llamada ethCall a la
         * direcci&oacute;n cero, que nunca retornar&iacute;a datos &uacute;tiles.
         */
        @Test
        void addressEsCero_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress())
                .thenReturn("0x0000000000000000000000000000000000000000");

            assertThatThrownBy(() -> service.getProposalCount())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        /**
         * ¿Qué estamos probando? Que getProposalCount lanza RuntimeException cuando
         * ethCall retorna un error.
         * ¿Por qué es importante? Si la llamada de consulta falla, no podemos confiar
         * en el resultado. Debemos propagar el error.
         * ¿Cómo funciona el setup? Mockeamos ethCall con hasError=true y mensaje de error.
         * El m&eacute;todo executeCall lo detecta y lanza excepci&oacute;n.
         * ¿Cuál es el resultado esperado? RuntimeException con "count failed".
         * ¿Qué pasaría si el código estuviera mal? Si no verificara hasError(),
         * intentar&iacute;a decodificar una respuesta de error y lanzar&iacute;a una excepci&oacute;n
         * de decodificaci&oacute;n confusa.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("count failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getProposalCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("count failed");
        }
    }

    @Nested
    @DisplayName("getProposal")
    class GetProposal {

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaDatos() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000001"
              + "000000000000000000000000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
              + "00000000000000000000000000000000000000000000000000000000000000a0"
              + "0000000000000000000000000000000000000000000000000000000000000001"
              + "00000000000000000000000000000000000000000000000000000000000000c0"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000004"
              + "7465737400000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000001"
              + "00");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            var result = service.getProposal(PROPOSAL_ID);

            assertThat(result).isNotEmpty();
        }

        /**
         * ¿Qué estamos probando? Que getProposal lanza IllegalStateException cuando
         * la direcci&oacute;n de governance es la direcci&oacute;n cero.
         * ¿Por qué es importante? Misma raz&oacute;n que en getProposalCount: no tiene
         * sentido consultar datos de un contrato no configurado.
         * ¿Cómo funciona el setup? Mockeamos props.getIdeaGovernanceAddress() con la
         * direcci&oacute;n cero.
         * ¿Cuál es el resultado esperado? IllegalStateException con "IdeaGovernance".
         * ¿Qué pasaría si el código estuviera mal? Se har&iacute;a una llamada ethCall a la
         * direcci&oacute;n cero y se obtendr&iacute;an datos basura o una excepci&oacute;n cr&iacute;ptica.
         */
        @Test
        void addressEsCero_lanzaIllegalState() {
            when(props.getIdeaGovernanceAddress())
                .thenReturn("0x0000000000000000000000000000000000000000");

            assertThatThrownBy(() -> service.getProposal(PROPOSAL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IdeaGovernance");
        }

        /**
         * ¿Qué estamos probando? Que getProposal lanza RuntimeException cuando
         * ethCall retorna un error.
         * ¿Por qué es importante? Si la consulta de una propuesta falla, no podemos
         * mostrar datos incorrectos al usuario.
         * ¿Cómo funciona el setup? Mockeamos ethCall con hasError=true y mensaje de error.
         * El m&eacute;todo executeCall lo detecta y lanza la excepci&oacute;n.
         * ¿Cuál es el resultado esperado? RuntimeException con "get failed".
         * ¿Qué pasaría si el código estuviera mal? Si no verificara hasError(),
         * devolver&iacute;a una lista vac&iacute;a o datos corruptos.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethCallFalla_lanzaRuntime() throws Exception {
            when(props.getIdeaGovernanceAddress()).thenReturn(GOVERNANCE_ADDR);
            when(credentials.getAddress()).thenReturn(BACKEND_ADDR);
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("get failed");
            when(ethCall.getError()).thenReturn(error);
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            assertThatThrownBy(() -> service.getProposal(PROPOSAL_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("get failed");
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
