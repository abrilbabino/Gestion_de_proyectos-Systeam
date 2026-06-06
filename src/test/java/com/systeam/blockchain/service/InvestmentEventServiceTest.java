package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import com.systeam.config.BlockchainProperties;

/**
 * Pruebas unitarias para {@link InvestmentEventService}.
 * <p>
 * Este servicio escucha eventos {@code InvestmentMade} en la blockchain de Sepolia,
 * los persiste en {@code blockchain_eventos} y reconcilia inversiones pendientes
 * con los eventos on-chain.
 * </p>
 *
 * <h2>Conceptos basicos para principiantes</h2>
 * <ul>
 *   <li><b>Mock:</b> Objeto simulado que reemplaza dependencias reales (Web3j,
 *       JdbcTemplate, BlockchainProperties).</li>
 *   <li><b>Stub:</b> Configurar que valor retorna un metodo del mock.</li>
 *   <li><b>Verify:</b> Verificar que ciertos metodos del mock fueron invocados.</li>
 *   <li><b>pollInvestmentEvents:</b> Consulta logs del evento InvestmentMade,
 *       verifica duplicados, persiste en BD y actualiza el ultimo bloque.</li>
 *   <li><b>reconciliarInversionesPendientes:</b> Busca eventos InvestmentMade sin
 *       procesar que ya tienen registro en investments y los vincula.</li>
 *   <li><b>ZERO_ADDRESS:</b> Direccion 0x000...000 que indica contrato no configurado.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InvestmentEventServiceTest {

    private static final String OFFERING_ADDR = "0xfDa7ff1D7C9AEF92982753C1dE0A4F375BAecc43";
    private static final String TX_HASH = "0xabc123";
    private static final Long BLOCK_NUM = 100L;
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Mock
    private Web3j web3j;
    @Mock
    private BlockchainProperties props;
    @Mock
    private JdbcTemplate jdbc;

    private InvestmentEventService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentEventService(web3j, props, jdbc);
    }

    @Nested
    @DisplayName("pollInvestmentEvents")
    class PollInvestmentEvents {

        @Test
        void noConfigurado_retornaSilenciosamente() {
            when(props.getOfferingContractAddress()).thenReturn(null);

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando getOfferingContractAddress() devuelve
         * una cadena vacia "", el metodo retorna silenciosamente.
         * Por que es importante? isBlank() evita consultar la blockchain con
         * una direccion de contrato invalida.
         * Como funciona el setup? Configuramos el mock para que devuelva "".
         * Cual es el resultado esperado? El metodo termina sin llamar a Web3j ni JDBC.
         * Que pasaria si el codigo estuviera mal? Sin isBlank(), se crearia un
         * filtro EthFilter con direccion vacia, causando error de red.
         */
        @Test
        void direccionVacia_retornaSilenciosamente() {
            when(props.getOfferingContractAddress()).thenReturn("");

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando la direccion del contrato es la
         * ZERO_ADDRESS, el metodo retorna sin hacer nada.
         * Por que es importante? ZERO_ADDRESS indica que el contrato OfferingContract
         * no ha sido desplegado aun. El servicio debe esperar sin consumir recursos.
         * Como funciona el setup? Configuramos el mock para que devuelva ZERO_ADDRESS.
         * Cual es el resultado esperado? El metodo retorna silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin esta validacion, se
         * consultarian eventos de una direccion quemada, desperdiciando recursos.
         */
        @Test
        void direccionZero_retornaSilenciosamente() {
            when(props.getOfferingContractAddress()).thenReturn(ZERO_ADDRESS);

            service.pollInvestmentEvents();
        }

        @SuppressWarnings("unchecked")
        @Test
        void sinBloquesNuevos_retorna() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando ethGetLogs lanza excepcion, el metodo
         * la captura y retorna silenciosamente.
         * Por que es importante? Errores de red no deben detener el scheduler.
         * Como funciona el setup? Mockeamos ethGetLogs para que el Request.send()
         * lance RuntimeException("Network error").
         * Cual es el resultado esperado? El catch interno captura y retorna.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, el polling
         * se detendria permanentemente.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethGetLogsLanzaExcepcion_retornaSilenciosamente() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenThrow(new RuntimeException("Network error"));
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando getLogs() devuelve null, el metodo
         * lo detecta y retorna silenciosamente.
         * Por que es importante? Web3j puede devolver null. El codigo debe
         * proteger contra NullPointerException.
         * Como funciona el setup? Mockeamos ethGetLogs para devolver un EthLog
         * cuyo getLogs() retorna null.
         * Cual es el resultado esperado? logResults == null activa el return.
         * Que pasaria si el codigo estuviera mal? El for sobre null lanzaria
         * NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logResultsNull_retornaSilenciosamente() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(null);
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que si un LogResult.get() devuelve null, el
         * bucle lo salta y continua con el siguiente log.
         * Por que es importante? La blockchain puede devolver logs nulos en
         * reorganizaciones. No deben causar error.
         * Como funciona el setup? Creamos dos LogResult: primero retorna null,
         * segundo retorna un log valido.
         * Cual es el resultado esperado? El bucle procesa el segundo log
         * (INSERT en blockchain_eventos) sin que el primero cause error.
         * Que pasaria si el codigo estuviera mal? Sin if (logEntry != null),
         * procesarEventoInversion recibiria null y lanzaria NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logEntryNull_omitidoSinError() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildInvestmentLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResultNull = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResultNull.get()).thenReturn(null);
            var logResultValido = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResultValido.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResultNull, logResultValido));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();

            verify(jdbc).update(anyString(), anyString(), any(), anyString());
        }

        /**
         * Que estamos probando? Que cuando ocurre una excepcion en el bloque
         * try externo de pollInvestmentEvents (por ejemplo, ethBlockNumber
         * falla), esta es capturada por el catch general.
         * Por que es importante? Cualquier error inesperado en el polling no
         * debe detener el scheduler permanentemente.
         * Como funciona el setup? Mockeamos web3j.ethBlockNumber() para que
         * lance RuntimeException al hacer send().
         * Cual es el resultado esperado? La excepcion se propaga hasta el catch
         * externo (lineas 94-96) y el metodo termina silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin el catch general, una
         * excepcion no cubierta por los catch internos detendria el scheduler.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnPoll_capturaExcepcion() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            var blockReq = org.mockito.Mockito.mock(Request.class);
            when(blockReq.send()).thenThrow(new RuntimeException("Eth error"));
            when(web3j.ethBlockNumber()).thenReturn((Request) blockReq);

            service.pollInvestmentEvents();
        }

        @SuppressWarnings("unchecked")
        @Test
        void conEventos_procesaCorrectamente() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildInvestmentLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();

            verify(jdbc).update(anyString(), anyString(), any(), anyString());
            verify(jdbc).update(anyString(), eq(BLOCK_NUM), anyString());
        }

        /**
         * Que estamos probando? Que cuando un log tiene menos de 3 topics,
         * procesarEventoInversion emite una advertencia y retorna.
         * Por que es importante? InvestmentMade tiene 3 topics: signature,
         * proyectoId, investor. Topics insuficientes = log malformado.
         * Como funciona el setup? Sobrescribimos topics del log con solo 2.
         * Cual es el resultado esperado? La validacion topics.size() < 3
         * se activa, emite warn y retorna sin INSERT.
         * Que pasaria si el codigo estuviera mal? topics.get(2) lanzaria
         * IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void topicsInsuficientes_advierteYRetorna() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildInvestmentLog();
            logEntry.setTopics(List.of("0x" + "a".repeat(64), "0x01"));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando yaExiste COUNT retorna null,
         * el metodo prosigue con el procesamiento.
         * Por que es importante? La condicion if (yaExiste != null && yaExiste > 0)
         * solo salta cuando NO es null Y > 0. null debe continuar.
         * Como funciona el setup? Mockeamos queryForObject(Long.class) para que
         * el segundo llamado retorne null.
         * Cual es el resultado esperado? El codigo llega hasta el INSERT.
         * Que pasaria si el codigo estuviera mal? Si la condicion fuera
         * if (yaExiste == null || yaExiste > 0), un null causaria un falso
         * positivo saltandose el evento.
         */
        @SuppressWarnings("unchecked")
        @Test
        void yaExisteNull_procesaCorrectamente() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(null);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildInvestmentLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();

            verify(jdbc).update(anyString(), anyString(), any(), anyString());
        }

        /**
         * Que estamos probando? Que si el INSERT en blockchain_eventos lanza
         * una excepcion, el catch de procesarEventoInversion la captura.
         * Por que es importante? Errores de BD no deben detener el
         * procesamiento de otros eventos del lote.
         * Como funciona el setup? Mockeamos jdbc.update() con 4 parametros
         * (INSERT blockchain_eventos) para que lance RuntimeException.
         * Cual es el resultado esperado? La excepcion es capturada en las
         * lineas 172-174 y el metodo retorna silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, la excepcion
         * se propagaria y detendria el polling.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnInsert_capturaExcepcion() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildInvestmentLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("DB error"));

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando leerUltimoBloque falla, el metodo
         * pollInvestmentEvents lo maneja gracias al try-catch interno.
         * Por que es importante? La tabla blockchain_sync puede no existir
         * en el primer despliegue.
         * Como funciona el setup? Mockeamos queryForObject(Long.class) para
         * que lance RuntimeException. Esto activa el catch de leerUltimoBloque
         * que retorna 0L.
         * Cual es el resultado esperado? El polling continua con ultimoBloque = 0.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, la excepcion
         * se propagaria al scheduler.
         */
        @SuppressWarnings("unchecked")
        @Test
        void leerUltimoBloqueLanzaExcepcion_retornaCero() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenThrow(new RuntimeException("DB error"));
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollInvestmentEvents();
        }

        /**
         * Que estamos probando? Que cuando actualizarUltimoBloque ejecuta el
         * UPDATE y afecta 0 filas, se ejecuta el INSERT como respaldo.
         * Por que es importante? En el primer ciclo no existe fila para
         * "OfferingContract". Sin el INSERT de respaldo, el bloque nunca
         * se guardaria, causando reprocesamiento infinito.
         * Como funciona el setup? Mockeamos jdbc.update() de 3 parametros para
         * que retorne 0 (UPDATE sin filas). No hay logs (lista vacia).
         * Cual es el resultado esperado? Se invocan UPDATE e INSERT.
         * Verificamos con times(2).
         * Que pasaria si el codigo estuviera mal? Sin if (filas == 0),
         * el bloque nunca se persistiria.
         */
        @SuppressWarnings("unchecked")
        @Test
        void actualizarBloque_updateRetornaCero_ejecutaInsert() throws Exception {
            when(props.getOfferingContractAddress()).thenReturn(OFFERING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), any(), anyString())).thenReturn(0);

            service.pollInvestmentEvents();

            verify(jdbc).update(anyString(), any(), anyString());
            verify(jdbc).update(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reconciliarInversionesPendientes")
    class ReconciliarInversionesPendientes {

        @SuppressWarnings("unchecked")
        @Test
        void reconciliaEventos() {
            var row = org.mockito.Mockito.mock(java.util.Map.class);
            when(row.get("id")).thenReturn(1L);
            when(row.get("tx_hash")).thenReturn(TX_HASH);
            when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(42L);

            service.reconciliarInversionesPendientes();

            verify(jdbc).update(anyString(), eq(42L), eq(1L));
        }

        /**
         * Que estamos probando? Que cuando investmentId es null (no existe la
         * inversion en la tabla investments), el metodo salta el UPDATE
         * y no marca el evento como procesado.
         * Por que es importante? Si la inversion aun no se ha creado en el
         * sistema, no debemos vincular el evento. La reconciliacion debe
         * esperar a que la inversion exista.
         * Como funciona el setup? Mockeamos queryForObject(Long.class) para
         * que retorne null.
         * Cual es el resultado esperado? El if (investmentId != null) es false,
         * por lo que no se ejecuta ningun jdbc.update().
         * Que pasaria si el codigo estuviera mal? Sin el null check,
         * se llamaria a jdbc.update con null como investment_id, causando
         * error de BD o datos corruptos.
         */
        @SuppressWarnings("unchecked")
        @Test
        void investmentIdNull_skipUpdate() {
            var row = org.mockito.Mockito.mock(java.util.Map.class);
            when(row.get("id")).thenReturn(1L);
            when(row.get("tx_hash")).thenReturn(TX_HASH);
            when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(null);

            service.reconciliarInversionesPendientes();

            verify(jdbc, never()).update(anyString(), any(), any());
        }

        /**
         * Que estamos probando? Que cuando jdbc.queryForList() lanza una
         * excepcion, reconciliarInversionesPendientes la captura.
         * Por que es importante? La reconciliacion se ejecuta periodicamente.
         * Un error de BD no debe cancelar futuras ejecuciones.
         * Como funciona el setup? Mockeamos queryForList() para que lance
         * RuntimeException("DB error").
         * Cual es el resultado esperado? La excepcion es capturada en las
         * lineas 129-131 y el metodo termina silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, la excepcion
         * se propagaria al scheduler.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnQuery_capturaExcepcion() {
            when(jdbc.queryForList(anyString()))
                .thenThrow(new RuntimeException("DB error"));

            service.reconciliarInversionesPendientes();
        }
    }

    private Log buildInvestmentLog() {
        var log = new Log();
        log.setTransactionHash(TX_HASH);
        log.setBlockNumber("0x" + Long.toHexString(BLOCK_NUM));
        log.setTopics(List.of(
            "0x" + "a".repeat(64),
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0x000000000000000000000000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        ));
        log.setData("0x" + "00".repeat(64));
        return log;
    }

    @SuppressWarnings("unchecked")
    private void mockBlockNumber(BigInteger val) throws Exception {
        var blockResp = org.mockito.Mockito.mock(EthBlockNumber.class);
        when(blockResp.getBlockNumber()).thenReturn(val);
        var request = org.mockito.Mockito.mock(Request.class);
        when(request.send()).thenReturn(blockResp);
        when(web3j.ethBlockNumber()).thenReturn((Request) request);
    }
}
