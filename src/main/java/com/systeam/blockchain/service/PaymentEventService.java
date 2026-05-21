package com.systeam.blockchain.service;

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
public class PaymentEventService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventService.class);

    private final Web3j web3j;
    private final BlockchainProperties props;
    private final JdbcTemplate jdbc;

    public PaymentEventService(Web3j web3j, BlockchainProperties props, JdbcTemplate jdbc) {
        this.web3j = web3j;
        this.props = props;
        this.jdbc = jdbc;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Scheduled(fixedDelayString = "${blockchain.event-poll-interval:30000}")
    public void pollPaidEvents() {
        String paymentGateway = props.getPaymentGatewayAddress();
        if (paymentGateway == null || paymentGateway.isBlank()) {
            return;
        }

        try {
            Long ultimoBloque = leerUltimoBloqueProcesado();

            BigInteger bloqueActual = web3j.ethBlockNumber().send().getBlockNumber();
            long actual = bloqueActual.longValue();

            if (actual <= ultimoBloque) {
                return;
            }

            @SuppressWarnings("rawtypes")
            Event paidEvent = new Event(
                "Paid",
                List.of(
                    new TypeReference<Uint256>() {},
                    new TypeReference<Bytes32>() {},
                    new TypeReference<Address>() {}
                )
            );

            String eventSignature = EventEncoder.encode(paidEvent);

            EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(ultimoBloque + 1)),
                DefaultBlockParameter.valueOf(bloqueActual),
                paymentGateway
            );
            filter.addSingleTopic(eventSignature);

            @SuppressWarnings("rawtypes")
            List<EthLog.LogResult> logResults;
            try {
                logResults = web3j.ethGetLogs(filter).send().getLogs();
            } catch (Exception e) {
                log.warn("Error consultando logs de Sepolia: {}", e.getMessage());
                return;
            }

            for (EthLog.LogResult result : logResults) {
                Log logEntry = (Log) result.get();
                if (logEntry != null) {
                    procesarEventoPaid(logEntry, paidEvent);
                }
            }

            actualizarUltimoBloque(actual);

        } catch (Exception e) {
            log.error("Error en pollPaidEvents: {}", e.getMessage());
        }
    }

    private Long leerUltimoBloqueProcesado() {
        try {
            Long bloque = jdbc.queryForObject(
                "SELECT ultimo_bloque FROM blockchain_sync WHERE nombre = ?",
                Long.class,
                "PaymentGateway"
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
                bloque,
                "PaymentGateway"
            );
            if (filas == 0) {
                jdbc.update(
                    "INSERT INTO blockchain_sync (nombre, ultimo_bloque) VALUES (?, ?)",
                    "PaymentGateway",
                    bloque
                );
            }
        } catch (Exception e) {
            log.warn("Error actualizando ultimo_bloque: {}", e.getMessage());
        }
    }

    private void procesarEventoPaid(Log logEntry, Event paidEvent) {
        try {
            String txHash = logEntry.getTransactionHash();
            BigInteger blockNumber = logEntry.getBlockNumber();

            List<String> topics = logEntry.getTopics();
            if (topics.size() < 4) {
                log.warn("Evento Paid inválido (topics insuficientes): {}", txHash);
                return;
            }

            String amountHex = topics.get(1);
            BigInteger amount = Numeric.toBigInt(amountHex);

            String actionIdHex = topics.get(2);

            String payerHex = topics.get(3);
            String payer = "0x" + payerHex.substring(payerHex.length() - 40);

            Long yaProcesado = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE tx_hash = ?",
                Long.class,
                txHash
            );
            if (yaProcesado != null && yaProcesado > 0) {
                return;
            }

            String datosJson = String.format(
                "{\"amount\":\"%s\",\"actionId\":\"%s\",\"payer\":\"%s\"}",
                amount.toString(), actionIdHex, payer
            );

            jdbc.update("""
                INSERT INTO blockchain_eventos
                    (tx_hash, block_number, tipo_evento, datos)
                VALUES (?, ?, 'Paid', ?)
                """,
                txHash,
                blockNumber.longValue(),
                datosJson
            );

            log.info("Evento Paid detectado: tx={}, amount={}, actionId={}, payer={}",
                txHash, amount, actionIdHex, payer);

            try {
                Long investmentId = jdbc.queryForObject(
                    "SELECT id FROM investments WHERE tx_hash = ? AND deleted_at IS NULL",
                    Long.class,
                    txHash
                );
                if (investmentId != null) {
                    jdbc.update(
                        "UPDATE blockchain_eventos SET procesado = TRUE, investment_id = ?, processed_at = NOW() WHERE tx_hash = ?",
                        investmentId, txHash
                    );
                    log.info("Evento vinculado a inversión {}: {}", investmentId, txHash);
                }
            } catch (Exception e) {
                log.debug("Evento Paid aún sin inversión vinculada: {}", txHash);
            }

        } catch (Exception e) {
            log.error("Error procesando evento Paid: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${blockchain.event-reconcile-interval:120000}")
    public void reconciliarEventosPendientes() {
        try {
            List<java.util.Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT e.id, e.tx_hash FROM blockchain_eventos e " +
                "WHERE e.procesado = FALSE " +
                "AND EXISTS (SELECT 1 FROM investments i WHERE i.tx_hash = e.tx_hash AND i.deleted_at IS NULL)"
            );

            for (var row : pendientes) {
                Long id = (Long) row.get("id");
                String txHash = (String) row.get("tx_hash");

                Long investmentId = jdbc.queryForObject(
                    "SELECT id FROM investments WHERE tx_hash = ? AND deleted_at IS NULL",
                    Long.class, txHash
                );

                jdbc.update(
                    "UPDATE blockchain_eventos SET procesado = TRUE, investment_id = ?, processed_at = NOW() WHERE id = ?",
                    investmentId, id
                );

                log.info("Evento {} reconciliado con inversión {}: {}", id, investmentId, txHash);
            }
        } catch (Exception e) {
            log.warn("Error en reconciliación de eventos: {}", e.getMessage());
        }
    }

    public long contarEventosProcesados() {
        try {
            Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE procesado = TRUE",
                Long.class
            );
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public long contarEventosPendientes() {
        try {
            Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE procesado = FALSE",
                Long.class
            );
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
