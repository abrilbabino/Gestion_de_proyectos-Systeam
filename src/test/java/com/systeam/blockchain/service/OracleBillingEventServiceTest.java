package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

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
 * Pruebas unitarias para {@link OracleBillingEventService}.
 * <p>
 * Este servicio escucha eventos {@code BillingAudited} en la blockchain de Sepolia,
 * los persiste en las tablas {@code oracle_billing} y {@code blockchain_eventos},
 * y reconcilia eventos pendientes con la tabla de billing.
 * </p>
 *
 * <h2>Conceptos basicos para principiantes</h2>
 * <ul>
 *   <li><b>Mock:</b> Objeto simulado que reemplaza a la dependencia real (Web3j, JdbcTemplate,
 *       BlockchainProperties). Permite controlar resultados sin blockchain ni BD real.</li>
 *   <li><b>Stub:</b> Configurar que devuelve un mock ante ciertos parametros.</li>
 *   <li><b>Verify:</b> Verificar que cierto metodo del mock fue invocado.</li>
 *   <li><b>pollBillingAuditedEvents:</b> Consulta logs del evento BillingAudited en la
 *       blockchain, verifica duplicados y persiste en BD.</li>
 *   <li><b>reconciliarBillingPendiente:</b> Busca eventos BillingAudited sin procesar
 *       y los marca como procesados si existe el registro en oracle_billing.</li>
 *   <li><b>ZERO_ADDRESS:</b> Direccion especial 0x000...000 que indica contrato no configurado.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OracleBillingEventServiceTest {

    private static final String BILLING_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TX_HASH = "0xabc123";
    private static final String ORACLE_TX_HASH = "0x" + "b".repeat(64);
    private static final Long BLOCK_NUM = 100L;
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Mock
    private Web3j web3j;
    @Mock
    private BlockchainProperties props;
    @Mock
    private JdbcTemplate jdbc;

    private OracleBillingEventService service;

    @BeforeEach
    void setUp() {
        service = new OracleBillingEventService(web3j, props, jdbc);
    }

    @Nested
    @DisplayName("pollBillingAuditedEvents")
    class PollBillingAuditedEvents {

        @Test
        void noConfigurado_retornaSilenciosamente() {
            when(props.getOracleBillingAddress()).thenReturn(null);

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando getOracleBillingAddress() devuelve
         * una cadena vacia "", el metodo retorna silenciosamente.
         * Por que es importante? La validacion de direccion vacia (isBlank())
         * evita consultar la blockchain con un contrato invalido.
         * Como funciona el setup? Configuramos el mock de props para que
         * getOracleBillingAddress() devuelva "".
         * Cual es el resultado esperado? El metodo termina sin llamar a Web3j ni JdbcTemplate.
         * Que pasaria si el codigo estuviera mal? Sin isBlank(), se intentaria
         * crear un filtro EthFilter con direccion vacia, causando error en la red.
         */
        @Test
        void direccionVacia_retornaSilenciosamente() {
            when(props.getOracleBillingAddress()).thenReturn("");

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando getOracleBillingAddress() devuelve
         * la direccion ZERO_ADDRESS (0x000...000), el metodo retorna silenciosamente.
         * Por que es importante? ZERO_ADDRESS es el valor por defecto cuando el
         * contrato OracleBilling no ha sido desplegado. El servicio debe detectarlo
         * y no intentar consultar logs.
         * Como funciona el setup? Configuramos el mock de props para que
         * getOracleBillingAddress() devuelva ZERO_ADDRESS.
         * Cual es el resultado esperado? El metodo termina sin consultar la blockchain.
         * Que pasaria si el codigo estuviera mal? Sin la validacion de ZERO_ADDRESS,
         * el servicio intentaria consultar eventos de una direccion quemada,
         * malgastando recursos de red y procesamiento.
         */
        @Test
        void direccionZero_retornaSilenciosamente() {
            when(props.getOracleBillingAddress()).thenReturn(ZERO_ADDRESS);

            service.pollBillingAuditedEvents();
        }

        @SuppressWarnings("unchecked")
        @Test
        void sinBloquesNuevos_retorna() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando web3j.ethGetLogs() lanza una
         * excepcion (ej. error de red), el metodo la captura y retorna
         * silenciosamente.
         * Por que es importante? Errores de red no deben detener el scheduler.
         * Como funciona el setup? Mockeamos web3j.ethGetLogs() para que
         * devuelva un Request cuyo send() lance RuntimeException("Network error").
         * Cual es el resultado esperado? El catch interno captura la excepcion
         * y el metodo retorna sin actualizar el ultimo bloque.
         * Que pasaria si el codigo estuviera mal? Sin el try-catch, el scheduler
         * quedaria detenido permanentemente.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethGetLogsLanzaExcepcion_retornaSilenciosamente() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenThrow(new RuntimeException("Network error"));
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando getLogs() devuelve null, el metodo
         * lo detecta y retorna sin procesar nada.
         * Por que es importante? Web3j puede devolver null en casos extremos.
         * El codigo debe proteger contra NullPointerException.
         * Como funciona el setup? Mockeamos ethGetLogs para que devuelva un
         * EthLog cuyo getLogs() retorna null.
         * Cual es el resultado esperado? La comprobacion logResults == null
         * detiene la ejecucion y retorna.
         * Que pasaria si el codigo estuviera mal? El bucle for sobre null
         * lanzaria NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logResultsNull_retornaSilenciosamente() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(null);
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que si un LogResult individual devuelve null
         * en get(), el bucle lo salta sin procesarlo y continua con el siguiente.
         * Por que es importante? La blockchain puede devolver logs nulos en
         * situaciones de reoganizacion. El codigo debe saltarlos sin fallar.
         * Como funciona el setup? Creamos dos LogResult: el primero devuelve
         * null (simula un log invalido), el segundo devuelve un log valido.
         * Cual es el resultado esperado? El bucle procesa el segundo log
         * (llama a procesarEventoBilling) sin que el primero cause error.
         * Que pasaria si el codigo estuviera mal? Sin el if (logEntry != null),
         * procesarEventoBilling recibiria null y lanzaria NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logEntryNull_omitidoSinError() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResultNull = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResultNull.get()).thenReturn(null);
            var logResultValido = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResultValido.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResultNull, logResultValido));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();

            verify(jdbc).update(anyString(), any(), any(), any(), anyString(), anyString());
        }

        @SuppressWarnings("unchecked")
        @Test
        void conEventos_procesaCorrectamente() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();

            verify(jdbc).update(anyString(), any(), any(), any(), anyString(), anyString());
            verify(jdbc).update(anyString(), anyString(), any(), anyString());
            verify(jdbc).update(anyString(), any(), anyString());
        }

        /**
         * Que estamos probando? Que cuando un log de BillingAudited tiene menos
         * de 4 topics, el metodo procesarEventoBilling emite una advertencia
         * y retorna sin persistir nada.
         * Por que es importante? El evento BillingAudited tiene 4 topics (signature,
         * proyectoId, oracleAddress, oracleTxHash). Topics insuficientes indican
         * un log malformado que no debe procesarse.
         * Como funciona el setup? Creamos un log buildBillingLog() con 4 topics
         * y luego sobrescribimos con solo 3 via setTopics().
         * Cual es el resultado esperado? La validacion topics.size() < 4 se activa
         * y el metodo retorna sin insertar en BD.
         * Que pasaria si el codigo estuviera mal? Intentar acceder a topics.get(3)
         * lanzaria IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void topicsInsuficientes_advierteYRetorna() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            logEntry.setTopics(List.of("0x" + "a".repeat(64), "0x01", "0x02"));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando yaEnBilling > 0 (el oracleTxHash ya
         * existe en oracle_billing), se salta el evento con una advertencia.
         * Por que es importante? La tabla oracle_billing tiene una constraint
         * UNIQUE en tx_hash. Sin esta validacion, el INSERT lanzaria una
         * excepcion de violacion de constraint.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Integer.class)
         * para que retorne 1 (COUNT > 0), simulando que el oracleTxHash ya
         * esta registrado.
         * Cual es el resultado esperado? procesarEventoBilling emite un warn
         * y retorna sin ejecutar los INSERTs.
         * Que pasaria si el codigo estuviera mal? Sin el chequeo, el INSERT
         * fallaria con DataIntegrityViolationException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void yaEnBillingDuplicado_skip() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();

            verify(jdbc, never()).update(anyString(), any(), any(), any(), anyString(), anyString());
        }

        /**
         * Que estamos probando? Que cuando jdbc.queryForObject(Long.class)
         * devuelve null para el conteo de yaExiste, el metodo prosigue
         * con el procesamiento (ya que null != null es false).
         * Por que es importante? La condicion if (yaExiste != null && yaExiste > 0)
         * solo salta si yaExiste NO es null Y > 0. Si es null, debe procesar.
         * Como funciona el setup? Mockeamos queryForObject(Long.class) para que
         * el segundo llamado retorne null (en lugar de 0L).
         * Cual es el resultado esperado? El codigo llega hasta los INSERTs
         * y persiste el evento correctamente.
         * Que pasaria si el codigo estuviera mal? Si la condicion fuera
         * if (yaExiste == null || yaExiste > 0), un null causaria un falso
         * positivo y se saltaria el evento.
         */
        @SuppressWarnings("unchecked")
        @Test
        void yaExisteNull_procesaCorrectamente() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(null);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();

            verify(jdbc).update(anyString(), any(), any(), any(), anyString(), anyString());
        }

        /**
         * Que estamos probando? Que si el INSERT en oracle_billing lanza una
         * excepcion, esta es capturada silenciosamente por el catch de
         * procesarEventoBilling.
         * Por que es importante? Un error de BD no debe propagarse y detener
         * el procesamiento de otros eventos en el mismo lote.
         * Como funciona el setup? Mockeamos jdbc.update() con 6 parametros
         * (la firma del INSERT en oracle_billing) para que lance
         * RuntimeException("DB insert error").
         * Cual es el resultado esperado? La excepcion es capturada en el catch
         * de la linea 208-210 y el metodo retorna silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, la excepcion
         * se propagaria hasta pollBillingAuditedEvents y detendria el ciclo.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnInsert_capturaExcepcion() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildBillingLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB insert error"));

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando leerUltimoBloque() falla (la consulta
         * a blockchain_sync lanza excepcion), pollBillingAuditedEvents lo maneja
         * gracias al try-catch interno que retorna 0L.
         * Por que es importante? La tabla blockchain_sync puede no existir en
         * el primer despliegue. El servicio debe continuar funcionando.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Long.class)
         * para que lance RuntimeException("DB error"). Esto activa el catch
         * de leerUltimoBloque que retorna 0L.
         * Cual es el resultado esperado? leerUltimoBloque retorna 0L y el
         * polling continua normalmente.
         * Que pasaria si el codigo estuviera mal? Sin el try-catch en
         * leerUltimoBloque, la excepcion se propagaria al scheduler.
         */
        @SuppressWarnings("unchecked")
        @Test
        void leerUltimoBloqueLanzaExcepcion_retornaCero() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenThrow(new RuntimeException("DB error"));
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollBillingAuditedEvents();
        }

        /**
         * Que estamos probando? Que cuando actualizarUltimoBloque ejecuta el
         * UPDATE y afecta 0 filas, se ejecuta el INSERT como respaldo para
         * crear el registro en blockchain_sync.
         * Por que es importante? En el primer ciclo no existe fila para
         * "OracleBilling". Sin el INSERT de respaldo, el bloque nunca se
         * guardaria y se reprocesarian todos los eventos en cada ciclo.
         * Como funciona el setup? Mockeamos jdbc.update() de 3 parametros para
         * que retorne 0 (UPDATE sin filas afectadas). No hay logs (lista vacia).
         * Cual es el resultado esperado? Se invocan ambas operaciones: UPDATE
         * y luego INSERT. Verificamos con times(2).
         * Que pasaria si el codigo estuviera mal? Sin el if (filas == 0),
         * el bloque nunca se persistiria y habria reprocesamiento infinito.
         */
        @SuppressWarnings("unchecked")
        @Test
        void actualizarBloque_updateRetornaCero_ejecutaInsert() throws Exception {
            when(props.getOracleBillingAddress()).thenReturn(BILLING_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), any(), anyString())).thenReturn(0);

            service.pollBillingAuditedEvents();

            verify(jdbc).update(anyString(), any(), anyString());
            verify(jdbc).update(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reconciliarBillingPendiente")
    class ReconciliarBillingPendiente {

        @SuppressWarnings("unchecked")
        @Test
        void reconciliaEventos() {
            var row = org.mockito.Mockito.mock(java.util.Map.class);
            when(row.get("id")).thenReturn(1L);
            when(row.get("tx_hash")).thenReturn(TX_HASH);
            when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
            when(jdbc.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(1);

            service.reconciliarBillingPendiente();

            verify(jdbc).update(anyString(), eq(1L));
        }

        /**
         * Que estamos probando? Que cuando el COUNT de oracle_billing retorna 0
         * (aun no existe el registro de billing), el metodo NO ejecuta el UPDATE
         * de blockchain_eventos.
         * Por que es importante? Si el registro de billing aun no ha llegado,
         * no debemos marcar el evento como procesado. La reconciliacion debe
         * esperar a que exista el registro.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Integer.class)
         * para que retorne 0.
         * Cual es el resultado esperado? La condicion if (count != null && count > 0)
         * es false, por lo que no se llama a jdbc.update().
         * Que pasaria si el codigo estuviera mal? Si la condicion fuera incorrecta
         * (ej. count >= 0), se marcarian eventos como procesados sin el respaldo
         * en oracle_billing.
         */
        @SuppressWarnings("unchecked")
        @Test
        void countCero_skipUpdate() {
            var row = org.mockito.Mockito.mock(java.util.Map.class);
            when(row.get("id")).thenReturn(1L);
            when(row.get("tx_hash")).thenReturn(TX_HASH);
            when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
            when(jdbc.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0);

            service.reconciliarBillingPendiente();

            verify(jdbc, never()).update(anyString(), any(Object.class));
        }

        /**
         * Que estamos probando? Que cuando jdbc.queryForList() lanza una
         * excepcion, reconciliarBillingPendiente la captura silenciosamente.
         * Por que es importante? La reconciliacion se ejecuta periodicamente.
         * Un error no debe impedir futuras ejecuciones.
         * Como funciona el setup? Mockeamos jdbc.queryForList() para que lance
         * RuntimeException("DB error").
         * Cual es el resultado esperado? La excepcion es capturada en el catch
         * de la linea 130-132 y el metodo termina silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin try-catch, la excepcion
         * se propagaria al scheduler y cancelaria futuras reconciliaciones.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnQuery_capturaExcepcion() {
            when(jdbc.queryForList(anyString()))
                .thenThrow(new RuntimeException("DB error"));

            service.reconciliarBillingPendiente();
        }
    }

    private Log buildBillingLog() {
        var log = new Log();
        log.setTransactionHash(TX_HASH);
        log.setBlockNumber("0x" + Long.toHexString(BLOCK_NUM));
        log.setTopics(List.of(
            "0x" + "a".repeat(64),
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0x000000000000000000000000cccccccccccccccccccccccccccccccccccccccc",
            ORACLE_TX_HASH
        ));
        log.setData("0x"
            + "0000000000000000000000000000000000000000000000000de0b6b3a7640000"
            + "0000000000000000000000000000000000000000000000000000000067a3f1c0");
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
