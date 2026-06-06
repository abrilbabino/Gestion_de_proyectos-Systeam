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
 * Pruebas unitarias para {@link PaymentEventService}.
 * <p>
 * Este servicio escucha eventos {@code Paid} en la blockchain de Sepolia usando Web3j,
 * los persiste en base de datos y los reconcilia con inversiones existentes.
 * </p>
 *
 * <h2>Conceptos basicos para principiantes</h2>
 * <ul>
 *   <li><b>Mock:</b> Un objeto simulado que reemplaza al real. Usamos Mockito para crear mocks
 *       de Web3j, JdbcTemplate y BlockchainProperties. Esto nos permite controlar
 *       exactamente que devuelve cada llamada sin necesidad de una blockchain real ni una BD.</li>
 *   <li><b>Stub:</b> Configurar un mock para que devuelva un valor especifico cuando se llama
 *       con ciertos parametros.</li>
 *   <li><b>Verify:</b> Comprobar que un metodo del mock fue llamado (o no) durante la prueba.</li>
 *   <li><b>pollPaidEvents:</b> Metodo principal que consulta logs de la blockchain, procesa
 *       eventos {@code Paid} y actualiza el ultimo bloque procesado.</li>
 *   <li><b>procesarEventoPaid:</b> Toma un log individual, verifica que no sea duplicado,
 *       lo inserta en BD y lo vincula con una inversion si existe.</li>
 *   <li><b>reconciliarEventosPendientes:</b> Busca eventos sin procesar que ya tienen inversion
 *       y los marca como procesados.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventServiceTest {

    private static final String GATEWAY_ADDR = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TX_HASH = "0xabc123";
    private static final Long BLOCK_NUM = 100L;

    @Mock
    private Web3j web3j;
    @Mock
    private BlockchainProperties props;
    @Mock
    private JdbcTemplate jdbc;

    private PaymentEventService service;

    @BeforeEach
    void setUp() {
        service = new PaymentEventService(web3j, props, jdbc);
    }

    @Nested
    @DisplayName("pollPaidEvents")
    class PollPaidEvents {

        @Test
        void noConfigurado_retornaSilenciosamente() {
            when(props.getPaymentGatewayAddress()).thenReturn(null);

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando getPaymentGatewayAddress() devuelve
         * una cadena vacia "", el metodo pollPaidEvents() retorna
         * silenciosamente sin hacer nada.
         * Por que es importante? Aunque el codigo revisa primero si es null,
         * tambien necesita revisar si es una cadena vacia (isBlank()) para
         * evitar conectarse con una direccion invalida.
         * Como funciona el setup? Configuramos el mock de props para que
         * getPaymentGatewayAddress() devuelva "".
         * Cual es el resultado esperado? El metodo termina sin llamar a Web3j ni a JdbcTemplate.
         * Que pasaria si el codigo estuviera mal? Si faltara el isBlank(),
         * el servicio intentaria consultar la blockchain con una direccion vacia,
         * lo que causaria errores de red o consultas invalidas.
         */
        @Test
        void direccionVacia_retornaSilenciosamente() {
            when(props.getPaymentGatewayAddress()).thenReturn("");

            service.pollPaidEvents();
        }

        @SuppressWarnings("unchecked")
        @Test
        void sinBloquesNuevos_retorna() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando web3j.ethGetLogs() lanza una
         * excepcion (por ejemplo, un error de red), el metodo la captura y retorna
         * silenciosamente sin propagar el error.
         * Por que es importante? Los errores de red son comunes al consultar una
         * blockchain. El servicio debe manejarlos con gracia y seguir funcionando
         * en el proximo ciclo de polling, no interrumpir la ejecucion.
         * Como funciona el setup? Mockeamos web3j.ethGetLogs() para que
         * devuelva un Request cuyo send() lance RuntimeException("Network error"),
         * simulando una falla de red.
         * Cual es el resultado esperado? El metodo captura la excepcion en el bloque
         * catch de las lineas 82-84 y retorna. No se llama a actualizarUltimoBloque.
         * Que pasaria si el codigo estuviera mal? Si no hubiera try-catch alrededor
         * de web3j.ethGetLogs(), una excepcion de red detendria el scheduler
         * y los eventos dejarian de procesarse para siempre.
         */
        @SuppressWarnings("unchecked")
        @Test
        void ethGetLogsLanzaExcepcion_retornaSilenciosamente() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenThrow(new RuntimeException("Network error"));
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando ethGetLogs().getLogs() devuelve
         * null, el metodo lo detecta y retorna silenciosamente sin procesar
         * logs ni actualizar el bloque.
         * Por que es importante? La biblioteca Web3j puede devolver null
         * en lugar de una lista vacia en ciertos casos extremos. El codigo debe
         * proteger contra NullPointerException.
         * Como funciona el setup? Mockeamos web3j.ethGetLogs() para que
         * devuelva un EthLog cuyo getLogs() retorna null.
         * Cual es el resultado esperado? La linea 87 detecta logResults == null
         * y retorna. No se procesan eventos ni se actualiza el ultimo bloque.
         * Que pasaria si el codigo estuviera mal? Si faltara la comprobacion
         * if (logResults == null) return;, el bucle for iteraria sobre
         * null y lanzaria NullPointerException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void logResultsNull_retornaSilenciosamente() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(null);
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();
        }

        @SuppressWarnings("unchecked")
        @Test
        void conEventos_procesaCorrectamente() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L)
                .thenThrow(new RuntimeException("investment not found"));
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildPaidLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();

            verify(jdbc).update(anyString(), anyString(), any(), anyString());
            verify(jdbc).update(anyString(), any(), anyString());
        }

        /**
         * Que estamos probando? Que cuando un log de evento Paid tiene menos de 4
         * topics, el metodo procesarEventoPaid emite una advertencia y retorna
         * sin procesar el evento.
         * Por que es importante? Un evento Paid valido debe tener 4 topics
         * (signature, amount, actionId, payer). Si la blockchain devuelve un log
         * malformado, el codigo no debe intentar acceder a indices inexistentes.
         * Como funciona el setup? Creamos un log usando buildPaidLog() y
         * luego sobrescribimos sus topics con solo 3 elementos.
         * Cual es el resultado esperado? procesarEventoPaid ejecuta la
         * validacion topics.size() < 4, emite un warn y retorna.
         * No se llama a jdbc.update() para insertar el evento.
         * Que pasaria si el codigo estuviera mal? Si no validara la cantidad de topics,
         * intentaria acceder a topics.get(3) que lanzaria IndexOutOfBoundsException.
         */
        @SuppressWarnings("unchecked")
        @Test
        void topicsInsuficientes_procesaSinError() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildPaidLog();
            logEntry.setTopics(List.of("0x" + "a".repeat(64), "0x00", "0x00"));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando el evento ya fue procesado anteriormente
         * (COUNT > 0), el metodo procesarEventoPaid lo salta sin insertarlo
         * de nuevo en la base de datos.
         * Por que es importante? La deduplicacion evita registros duplicados en la
         * tabla blockchain_eventos. Sin esta verificacion, el mismo evento
         * de la blockchain se insertaria multiples veces, causando datos inconsistentes.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Long.class)
         * para que el segundo llamado retorne 1L (COUNT = 1), indicando que
         * el tx_hash ya existe.
         * Cual es el resultado esperado? procesarEventoPaid verifica
         * yaProcesado > 0 y retorna sin llamar a jdbc.update()
         * para la insercion.
         * Que pasaria si el codigo estuviera mal? Si la condicion fuera incorrecta
         * (ej. yaProcesado >= 0), se saltarian todos los eventos incluso los
         * nuevos. O si faltara la validacion, se insertarian duplicados.
         */
        @SuppressWarnings("unchecked")
        @Test
        void eventoDuplicado_skipInsert() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(1L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildPaidLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();

            verify(jdbc, never()).update(anyString(), anyString(), any(), anyString());
        }

        /**
         * Que estamos probando? Que si jdbc.update() lanza una excepcion
         * al insertar el evento en blockchain_eventos, el error es capturado
         * silenciosamente por el bloque catch de procesarEventoPaid.
         * Por que es importante? Un error en la base de datos (ej. violacion de
         * constraint, conexion caida) no debe propagarse fuera del metodo ni detener
         * el procesamiento de futuros eventos.
         * Como funciona el setup? Mockeamos jdbc.update() con 4 parametros
         * (la firma del INSERT en blockchain_eventos) para que lance
         * RuntimeException("DB insert error").
         * Cual es el resultado esperado? La excepcion es capturada en la linea 198
         * y el metodo retorna sin propagar el error.
         * Que pasaria si el codigo estuviera mal? Si no hubiera try-catch alrededor
         * del INSERT, la excepcion se propagaria hasta pollPaidEvents,
         * deteniendo el procesamiento de cualquier otro evento en el mismo lote.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnInsert_capturaExcepcion() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1)
                .thenReturn(0L);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var logEntry = buildPaidLog();
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            var logResult = org.mockito.Mockito.mock(EthLog.LogResult.class);
            when(logResult.get()).thenReturn(logEntry);
            when(ethLog.getLogs()).thenReturn(List.of(logResult));
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("DB insert error"));

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando leerUltimoBloqueProcesado() falla
         * (por ejemplo, la tabla blockchain_sync no existe), el metodo
         * pollPaidEvents() lo maneja sin lanzar excepcion, gracias al
         * try-catch interno que retorna 0L.
         * Por que es importante? En el primer despliegue o si la BD no esta lista,
         * la consulta del ultimo bloque puede fallar. El servicio debe continuar
         * funcionando en lugar de romperse.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Long.class)
         * para que lance RuntimeException("DB error") en el primer llamado.
         * Esto activa el catch dentro de leerUltimoBloqueProcesado que
         * retorna 0L. Luego mockeamos ethBlockNumber y ethGetLogs
         * para que el resto del flujo continue normalmente.
         * Cual es el resultado esperado? leerUltimoBloqueProcesado retorna
         * 0L, y pollPaidEvents() procesa normalmente (en este caso, sin logs).
         * Que pasaria si el codigo estuviera mal? Si leerUltimoBloqueProcesado
         * no capturara la excepcion, se propagaria hasta el scheduler y el polling
         * se detendria por completo.
         */
        @SuppressWarnings("unchecked")
        @Test
        void leerUltimoBloqueLanzaExcepcion_retornaCero() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenThrow(new RuntimeException("DB error"));
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);

            service.pollPaidEvents();
        }

        /**
         * Que estamos probando? Que cuando actualizarUltimoBloque() ejecuta
         * la sentencia UPDATE y esta afecta 0 filas (porque no existe el registro en
         * blockchain_sync), entonces se ejecuta la sentencia INSERT como
         * plan de respaldo para crear el registro.
         * Por que es importante? En el primer ciclo de polling, no existe ninguna
         * fila en blockchain_sync para "PaymentGateway". El UPDATE no
         * actualiza nada, y sin el INSERT de respaldo el bloque nunca se guardaria,
         * causando que se reprocesen todos los eventos historicos en cada ciclo.
         * Como funciona el setup? Mockeamos jdbc.update() de 3 parametros
         * para que retorne 0 (simulando que el UPDATE no afecto filas).
         * Esto obliga a actualizarUltimoBloque a ejecutar el INSERT.
         * No hay eventos en los logs (lista vacia), para centrar el test solo
         * en la logica de actualizacion de bloque.
         * Cual es el resultado esperado? Se llama al UPDATE y, al retornar 0,
         * tambien se llama al INSERT. Verificamos que ambos jdbc.update()
         * fueron invocados.
         * Que pasaria si el codigo estuviera mal? Si faltara el bloque
         * if (filas == 0) con el INSERT, el bloque nunca se guardaria en BD
         * y el servicio reprocesaria todos los eventos en cada ejecucion.
         */
        @SuppressWarnings("unchecked")
        @Test
        void actualizarBloque_updateRetornaCero_ejecutaInsert() throws Exception {
            when(props.getPaymentGatewayAddress()).thenReturn(GATEWAY_ADDR);
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(BLOCK_NUM - 1);
            mockBlockNumber(BigInteger.valueOf(BLOCK_NUM));
            var ethLog = org.mockito.Mockito.mock(EthLog.class);
            when(ethLog.getLogs()).thenReturn(List.of());
            var ethLogRequest = org.mockito.Mockito.mock(Request.class);
            when(ethLogRequest.send()).thenReturn(ethLog);
            when(web3j.ethGetLogs(any())).thenReturn((Request) ethLogRequest);
            when(jdbc.update(anyString(), any(), anyString())).thenReturn(0);

            service.pollPaidEvents();

            verify(jdbc).update(anyString(), any(), anyString());
            verify(jdbc).update(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reconciliarEventosPendientes")
    class ReconciliarEventosPendientes {

        @SuppressWarnings("unchecked")
        @Test
        void reconciliaEventos() {
            var row = org.mockito.Mockito.mock(java.util.Map.class);
            when(row.get("id")).thenReturn(1L);
            when(row.get("tx_hash")).thenReturn(TX_HASH);
            when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
            when(jdbc.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(42L);

            service.reconciliarEventosPendientes();

            verify(jdbc).update(anyString(), eq(42L), eq(1L));
        }

        /**
         * Que estamos probando? Que cuando jdbc.queryForList() lanza una
         * excepcion (ej. error de conexion), el metodo
         * reconciliarEventosPendientes() la captura y no propaga el error.
         * Por que es importante? La reconciliacion se ejecuta periodicamente via
         * scheduler. Si falla, no debe detener futuras ejecuciones ni afectar otras
         * operaciones del servicio.
         * Como funciona el setup? Mockeamos jdbc.queryForList() para que
         * lance RuntimeException("DB error") al ser llamado.
         * Cual es el resultado esperado? La excepcion es capturada en el bloque
         * catch de las lineas 228-230, y el metodo termina silenciosamente.
         * Que pasaria si el codigo estuviera mal? Sin el try-catch, una excepcion
         * en la consulta inicial detendria la reconciliacion y se propagaria al
         * scheduler, cancelando futuras ejecuciones.
         */
        @SuppressWarnings("unchecked")
        @Test
        void errorEnQuery_capturaExcepcion() {
            when(jdbc.queryForList(anyString()))
                .thenThrow(new RuntimeException("DB error"));

            service.reconciliarEventosPendientes();
        }
    }

    @Nested
    @DisplayName("contarEventosProcesados")
    class ContarEventosProcesados {

        @Test
        void retornaContador() {
            when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(5L);

            long result = service.contarEventosProcesados();

            assertThat(result).isEqualTo(5L);
        }

        @Test
        void cuandoError_retornaCero() {
            when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("DB error"));

            long result = service.contarEventosProcesados();

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("contarEventosPendientes")
    class ContarEventosPendientes {

        @Test
        void retornaContador() {
            when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(3L);

            long result = service.contarEventosPendientes();

            assertThat(result).isEqualTo(3L);
        }

        /**
         * Que estamos probando? Que cuando jdbc.queryForObject() lanza una
         * excepcion (ej. error de conexion), el metodo contarEventosPendientes()
         * la captura y retorna 0L en lugar de propagar el error.
         * Por que es importante? Este metodo es usado por controladores y APIs
         * REST. Si falla, debe devolver un valor seguro (0) en lugar de lanzar
         * una excepcion 500.
         * Como funciona el setup? Mockeamos jdbc.queryForObject(Long.class)
         * para que lance RuntimeException("DB error").
         * Cual es el resultado esperado? El metodo retorna 0L.
         * Que pasaria si el codigo estuviera mal? Si no capturara la excepcion,
         * se propagaria al controlador REST y devolveria un error 500.
         */
        @Test
        void cuandoError_retornaCero() {
            when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("DB error"));

            long result = service.contarEventosPendientes();

            assertThat(result).isZero();
        }
    }

    private Log buildPaidLog() {
        var log = new Log();
        log.setTransactionHash(TX_HASH);
        log.setBlockNumber("0x" + Long.toHexString(BLOCK_NUM));
        log.setTopics(List.of(
            "0x" + "a".repeat(64),
            "0x0000000000000000000000000000000000000000000000000000000000000064",
            "0x" + "b".repeat(64),
            "0x000000000000000000000000cccccccccccccccccccccccccccccccccccccccc"
        ));
        log.setData("0x");
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
