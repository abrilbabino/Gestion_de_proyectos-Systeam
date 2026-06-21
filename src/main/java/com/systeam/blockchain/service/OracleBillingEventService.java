package com.systeam.blockchain.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import com.systeam.config.BlockchainProperties;

@Service
public class OracleBillingEventService {

    private static final Logger log = LoggerFactory.getLogger(OracleBillingEventService.class);
    private static final String SYNC_NAME = "OracleBilling";
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final Web3j web3j;
    private final BlockchainProperties props;
    private final JdbcTemplate jdbc;

    public OracleBillingEventService(Web3j web3j, BlockchainProperties props, JdbcTemplate jdbc) {
        this.web3j = web3j;
        this.props = props;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${blockchain.event-poll-interval:30000}")
    public void pollBillingAuditedEvents() {
        String contractAddress = props.getOracleBillingAddress();
        if (contractAddress == null || contractAddress.isBlank() || contractAddress.equals(ZERO_ADDRESS)) {
            return;
        }

        try {
            Long ultimoBloque = leerUltimoBloque();
            BigInteger bloqueActual = web3j.ethBlockNumber().send().getBlockNumber();
            long actual = bloqueActual.longValue();

            if (actual <= ultimoBloque) return;

            // BillingAudited(uint256 indexed proyectoId, uint256 billingAmount,
            //                address indexed oracleAddress, bytes32 indexed txHash, uint256 timestamp)
            Event billingEvent = new Event(
                "BillingAudited",
                List.of(
                    new TypeReference<Uint256>(true)  {},   // proyectoId  (indexed)
                    new TypeReference<Uint256>()       {},   // billingAmount
                    new TypeReference<Address>(true)   {},   // oracleAddress (indexed)
                    new TypeReference<Bytes32>(true)   {},   // txHash (indexed)
                    new TypeReference<Uint256>()       {}    // timestamp
                )
            );

            String eventSignature = EventEncoder.encode(billingEvent);

            EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(ultimoBloque + 1)),
                DefaultBlockParameter.valueOf(bloqueActual),
                contractAddress
            );
            filter.addSingleTopic(eventSignature);

            List<EthLog.LogResult> logResults;
            try {
                logResults = web3j.ethGetLogs(filter).send().getLogs();
            } catch (Exception e) {
                log.warn("Error consultando logs de OracleBilling: {}", e.getMessage());
                return;
            }

            if (logResults == null) return;

            for (EthLog.LogResult result : logResults) {
                Log logEntry = (Log) result.get();
                if (logEntry != null) {
                    procesarEventoBilling(logEntry);
                }
            }

            actualizarUltimoBloque(actual);

        } catch (Exception e) {
            log.error("Error en pollBillingAuditedEvents: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${blockchain.event-reconcile-interval:120000}")
    public void reconciliarBillingPendiente() {
        try {
            List<java.util.Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT e.id, e.tx_hash FROM blockchain_eventos e " +
                "WHERE e.procesado = FALSE AND e.tipo_evento = 'BillingAudited'"
            );

            for (var row : pendientes) {
                Long eventId = (Long) row.get("id");
                String ethTxHash = (String) row.get("tx_hash");

                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oracle_billing WHERE tx_hash IN " +
                    "(SELECT datos::json->>'oracleTxHash' FROM blockchain_eventos WHERE id = ?)",
                    Integer.class, eventId
                );

                if (count != null && count > 0) {
                    jdbc.update(
                        "UPDATE blockchain_eventos SET procesado = TRUE, processed_at = NOW() WHERE id = ?",
                        eventId
                    );
                    log.info("Evento BillingAudited reconciliado: ethTx={}", ethTxHash);
                }
            }
        } catch (Exception e) {
            log.warn("Error en reconciliación de billing: {}", e.getMessage());
        }
    }

    private void procesarEventoBilling(Log logEntry) {
        try {
            String ethTxHash = logEntry.getTransactionHash();
            BigInteger blockNumber = logEntry.getBlockNumber();

            Long yaExiste = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE tx_hash = ?",
                Long.class, ethTxHash
            );
            if (yaExiste != null && yaExiste > 0) return;

            List<String> topics = logEntry.getTopics();
            if (topics.size() < 4) {
                log.warn("Evento BillingAudited inválido (topics insuficientes): {}", ethTxHash);
                return;
            }

            // topic[0] = event signature
            // topic[1] = proyectoId (indexed uint256)
            // topic[2] = oracleAddress (indexed address)
            // topic[3] = oracleTxHash (indexed bytes32)
            BigInteger proyectoId   = Numeric.toBigInt(topics.get(1));
            String oracleAddressHex = topics.get(2);
            String oracleAddress    = "0x" + oracleAddressHex.substring(oracleAddressHex.length() - 40);
            String oracleTxHash     = topics.get(3); // bytes32 as 0x-prefixed hex

            // data = billingAmount (32 bytes) + timestamp (32 bytes)
            String data = logEntry.getData();
            BigInteger billingAmountWei = Numeric.toBigInt(data.substring(2, 66));
            BigInteger timestampSeconds = Numeric.toBigInt(data.substring(66, 130));

            // NUMERIC(38,18): divide by 1e18 to get the decimal representation
            BigDecimal montoFacturado = new BigDecimal(billingAmountWei)
                .divide(BigDecimal.TEN.pow(18));

            // Deduplication via UNIQUE constraint on oracle_billing.tx_hash
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oracle_billing WHERE tx_hash = ?",
                Integer.class, oracleTxHash
            );
            if (count != null && count > 0) {
                log.warn("oracleTxHash ya registrado en oracle_billing: {}", oracleTxHash);
                return;
            }

            jdbc.update("""
                INSERT INTO oracle_billing
                (proyecto_id, monto_facturado, fecha_reporte, oracle_address, tx_hash, procesado)
                VALUES (?, ?, to_timestamp(?), ?, ?, FALSE)
                """,
                proyectoId.longValue(),
                montoFacturado,
                timestampSeconds.longValue(),
                oracleAddress,
                oracleTxHash
            );

            String datosJson = String.format(
                "{\"proyectoId\":\"%s\",\"oracleAddress\":\"%s\",\"oracleTxHash\":\"%s\",\"montoFacturado\":\"%s\"}",
                proyectoId, oracleAddress, oracleTxHash, montoFacturado.toPlainString()
            );

            jdbc.update("""
                INSERT INTO blockchain_eventos
                    (tx_hash, block_number, tipo_evento, datos)
                VALUES (?, ?, 'BillingAudited', ?)
                """,
                ethTxHash, blockNumber.longValue(), datosJson
            );

            log.info("BillingAudited persistido: proyectoId={}, monto={}, oracleTx={}",
                proyectoId, montoFacturado.toPlainString(), oracleTxHash);

        } catch (Exception e) {
            log.error("Error procesando BillingAudited: {}", e.getMessage());
        }
    }

    private Long leerUltimoBloque() {
        try {
            Long bloque = jdbc.queryForObject(
                "SELECT ultimo_bloque FROM blockchain_sync WHERE nombre = ?",
                Long.class, SYNC_NAME
            );
            return bloque != null ? bloque : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void actualizarUltimoBloque(long bloque) {
        try {
            int filas = jdbc.update(
                "UPDATE blockchain_sync SET ultimo_bloque = ?, updated_at = NOW() WHERE nombre = ?",
                bloque, SYNC_NAME
            );
            if (filas == 0) {
                jdbc.update(
                    "INSERT INTO blockchain_sync (nombre, ultimo_bloque) VALUES (?, ?)",
                    SYNC_NAME, bloque
                );
            }
        } catch (Exception e) {
            log.warn("Error actualizando ultimo_bloque: {}", e.getMessage());
        }
    }
}
