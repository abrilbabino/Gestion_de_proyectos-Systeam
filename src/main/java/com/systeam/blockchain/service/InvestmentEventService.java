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
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import com.systeam.config.BlockchainProperties;
import org.web3j.utils.Numeric;

@Service
public class InvestmentEventService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentEventService.class);
    private static final String SYNC_NAME = "OfferingContract";

    private final Web3j web3j;
    private final BlockchainProperties props;
    private final JdbcTemplate jdbc;

    public InvestmentEventService(Web3j web3j, BlockchainProperties props, JdbcTemplate jdbc) {
        this.web3j = web3j;
        this.props = props;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${blockchain.event-poll-interval:30000}")
    public void pollInvestmentEvents() {
        String offeringContract = props.getOfferingContractAddress();
        if (offeringContract == null || offeringContract.isBlank()
            || offeringContract.equals("0x0000000000000000000000000000000000000000")) {
            return;
        }

        try {
            Long ultimoBloque = leerUltimoBloque();
            BigInteger bloqueActual = web3j.ethBlockNumber().send().getBlockNumber();
            long actual = bloqueActual.longValue();

            if (actual <= ultimoBloque) return;

            Event investmentEvent = new Event(
                "InvestmentMade",
                List.of(
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>() {},
                    new TypeReference<Uint256>() {}
                )
            );

            String eventSignature = EventEncoder.encode(investmentEvent);

            EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(ultimoBloque + 1)),
                DefaultBlockParameter.valueOf(bloqueActual),
                offeringContract
            );
            filter.addSingleTopic(eventSignature);

            List<EthLog.LogResult> logResults;
            try {
                logResults = web3j.ethGetLogs(filter).send().getLogs();
            } catch (Exception e) {
                log.warn("Error consultando logs de OfferingContract: {}", e.getMessage());
                return;
            }

            if (logResults == null) return;

            for (EthLog.LogResult result : logResults) {
                Log logEntry = (Log) result.get();
                if (logEntry != null) {
                    procesarEventoInversion(logEntry, investmentEvent);
                }
            }

            actualizarUltimoBloque(actual);

        } catch (Exception e) {
            log.error("Error en pollInvestmentEvents: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${blockchain.event-reconcile-interval:120000}")
    public void reconciliarInversionesPendientes() {
        try {
            List<java.util.Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT e.id, e.tx_hash, e.datos FROM blockchain_eventos e " +
                "WHERE e.procesado = FALSE AND e.tipo_evento = 'InvestmentMade' " +
                "AND EXISTS (SELECT 1 FROM investments i WHERE i.tx_hash = e.tx_hash)"
            );

            for (var row : pendientes) {
                Long eventId = (Long) row.get("id");
                String txHash = (String) row.get("tx_hash");

                Long investmentId = jdbc.queryForObject(
                    "SELECT id FROM investments WHERE tx_hash = ?",
                    Long.class, txHash
                );

                if (investmentId != null) {
                    jdbc.update(
                        "UPDATE blockchain_eventos SET procesado = TRUE, investment_id = ?, processed_at = NOW() WHERE id = ?",
                        investmentId, eventId
                    );
                    jdbc.update(
                        "UPDATE investments SET estado = 'CONFIRMADA', updated_at = NOW() WHERE id = ? AND estado = 'PENDIENTE'",
                        investmentId
                    );
                    log.info("Inversión {} reconciliada desde evento on-chain: {}", investmentId, txHash);
                }
            }
        } catch (Exception e) {
            log.warn("Error en reconciliación de inversiones: {}", e.getMessage());
        }
    }

    private void procesarEventoInversion(Log logEntry, Event event) {
        try {
            String txHash = logEntry.getTransactionHash();
            BigInteger blockNumber = logEntry.getBlockNumber();

            Long yaExiste = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blockchain_eventos WHERE tx_hash = ?",
                Long.class, txHash
            );
            if (yaExiste != null && yaExiste > 0) return;

            List<String> topics = logEntry.getTopics();
            if (topics.size() < 3) {
                log.warn("Evento InvestmentMade inválido (topics insuficientes): {}", txHash);
                return;
            }

            String proyectoIdHex = topics.get(1);
            BigInteger proyectoId = Numeric.toBigInt(proyectoIdHex);
            String investorHex = topics.get(2);
            String investor = "0x" + investorHex.substring(investorHex.length() - 40);

            String datosJson = String.format(
                "{\"proyectoId\":\"%s\",\"investor\":\"%s\"}",
                proyectoId.toString(), investor
            );

            jdbc.update("""
                INSERT INTO blockchain_eventos
                    (tx_hash, block_number, tipo_evento, datos)
                VALUES (?, ?, 'InvestmentMade', ?)
                """,
                txHash, blockNumber.longValue(), datosJson
            );

            log.info("Evento InvestmentMade detectado: tx={}, proyectoId={}, investor={}",
                txHash, proyectoId, investor);

        } catch (Exception e) {
            log.error("Error procesando InvestmentMade: {}", e.getMessage());
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
