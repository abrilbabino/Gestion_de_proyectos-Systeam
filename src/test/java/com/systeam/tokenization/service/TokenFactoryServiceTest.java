package com.systeam.tokenization.service;

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

@ExtendWith(MockitoExtension.class)
class TokenFactoryServiceTest {

    private static final String FACTORY_ADDR = "0x1234567890123456789012345678901234567890";
    private static final String PROJECT_TOKEN = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final String CREATED_TOKEN = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final Long PROYECTO_ID = 1L;

    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private TransactionManager txManager;

    private BlockchainProperties props;
    private TokenFactoryService service;

    @BeforeEach
    void setUp() {
        props = new BlockchainProperties();
        props.setTokenFactoryAddress(FACTORY_ADDR);
        props.setProjectTokenAddress(PROJECT_TOKEN);
        service = new TokenFactoryService(web3j, credentials, txManager, props);
    }

    @Nested
    @DisplayName("crearTokenProyecto")
    class CrearTokenProyecto {

        @Test
        void cuandoFactoryNoConfigurada_retornaProjectToken() throws Exception {
            props.setTokenFactoryAddress("0x0000000000000000000000000000000000000000");

            String result = service.crearTokenProyecto(PROYECTO_ID, "Test", "TST",
                BigInteger.valueOf(1000));

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @Test
        void cuandoFactoryEsNull_retornaProjectToken() throws Exception {
            props.setTokenFactoryAddress(null);

            String result = service.crearTokenProyecto(PROYECTO_ID, "Test", "TST",
                BigInteger.valueOf(1000));

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void exito_conReceiptValido() throws Exception {
            mockGasPrice();

            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn("0xtx1");
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            var receiptLog = new org.web3j.protocol.core.methods.response.Log();
            receiptLog.setData("0x000000000000000000000000" + CREATED_TOKEN.substring(2));
            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setLogs(List.of(receiptLog));
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));

            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt("0xtx1")).thenReturn((Request) receiptRequest);

            String result = service.crearTokenProyecto(PROYECTO_ID, "Nombre", "SYM",
                BigInteger.valueOf(5000));

            assertThat(result).isEqualTo(CREATED_TOKEN);
            verify(txManager).sendTransaction(any(), eq(BigInteger.valueOf(500_000L)),
                eq(FACTORY_ADDR), anyString(), eq(BigInteger.ZERO));
        }

        @SuppressWarnings("unchecked")
        @Test
        void txFalla_conError_lanzaRuntime() throws Exception {
            mockGasPrice();

            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(true);
            var error = new org.web3j.protocol.core.Response.Error();
            error.setMessage("gas too low");
            when(txResponse.getError()).thenReturn(error);
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            assertThatThrownBy(() -> service.crearTokenProyecto(PROYECTO_ID, "Fail", "FL",
                BigInteger.valueOf(100)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("gas too low");
        }

        @SuppressWarnings("unchecked")
        @Test
        void receiptVacio_retornaProjectToken() throws Exception {
            mockGasPrice();

            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn("0xtx2");
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.empty());
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt("0xtx2")).thenReturn((Request) receiptRequest);

            String result = service.crearTokenProyecto(PROYECTO_ID, "NoReceipt", "NR",
                BigInteger.valueOf(100));

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void logsVacios_retornaProjectToken() throws Exception {
            mockGasPrice();

            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn("0xtx3");
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setLogs(List.of());
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt("0xtx3")).thenReturn((Request) receiptRequest);

            String result = service.crearTokenProyecto(PROYECTO_ID, "NoLogs", "NL",
                BigInteger.valueOf(100));

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void eventDataVacio_retornaProjectToken() throws Exception {
            mockGasPrice();

            var txResponse = org.mockito.Mockito.mock(EthSendTransaction.class);
            when(txResponse.hasError()).thenReturn(false);
            when(txResponse.getTransactionHash()).thenReturn("0xtx4");
            when(txManager.sendTransaction(any(), any(), anyString(), anyString(), any()))
                .thenReturn(txResponse);

            var receiptLog = new org.web3j.protocol.core.methods.response.Log();
            receiptLog.setData("0x");
            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setLogs(List.of(receiptLog));
            var receiptResponse = org.mockito.Mockito.mock(EthGetTransactionReceipt.class);
            when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            var receiptRequest = org.mockito.Mockito.mock(Request.class);
            when(receiptRequest.send()).thenReturn(receiptResponse);
            when(web3j.ethGetTransactionReceipt("0xtx4")).thenReturn((Request) receiptRequest);

            String result = service.crearTokenProyecto(PROYECTO_ID, "NoData", "ND",
                BigInteger.valueOf(100));

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }
    }

    @Nested
    @DisplayName("obtenerTokenDeProyecto")
    class ObtenerTokenDeProyecto {

        @Test
        void cuandoFactoryNoConfigurada_retornaProjectToken() throws Exception {
            props.setTokenFactoryAddress("0x0000000000000000000000000000000000000000");

            String result = service.obtenerTokenDeProyecto(PROYECTO_ID);

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void exito_retornaDireccionToken() throws Exception {
            when(credentials.getAddress()).thenReturn("0xuser");
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
        void direccionCero_retornaProjectToken() throws Exception {
            when(credentials.getAddress()).thenReturn("0xuser");
            var ethCall = org.mockito.Mockito.mock(EthCall.class);
            when(ethCall.hasError()).thenReturn(false);
            when(ethCall.getValue()).thenReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");
            var request = org.mockito.Mockito.mock(Request.class);
            when(request.send()).thenReturn(ethCall);
            when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) request);

            String result = service.obtenerTokenDeProyecto(PROYECTO_ID);

            assertThat(result).isEqualTo(PROJECT_TOKEN);
        }

        @SuppressWarnings("unchecked")
        @Test
        void errorEnConsulta_lanzaRuntime() throws Exception {
            when(credentials.getAddress()).thenReturn("0xuser");
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

    @SuppressWarnings("unchecked")
    private void mockGasPrice() throws Exception {
        var request = org.mockito.Mockito.mock(Request.class);
        var gasPriceResponse = org.mockito.Mockito.mock(EthGasPrice.class);
        when(gasPriceResponse.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
        when(request.send()).thenReturn(gasPriceResponse);
        when(web3j.ethGasPrice()).thenReturn((Request) request);
    }
}
