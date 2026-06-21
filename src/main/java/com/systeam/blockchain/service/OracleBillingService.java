package com.systeam.blockchain.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

@Service
public class OracleBillingService {

    private static final Logger log = LoggerFactory.getLogger(OracleBillingService.class);
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final int RECEIPT_POLL_ATTEMPTS = 40;
    private static final long RECEIPT_POLL_INTERVAL_MS = 3_000L;

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    public OracleBillingService(Web3j web3j, Credentials credentials,
                                 BlockchainProperties props, JdbcTemplate jdbc) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props, jdbc);
    }

    OracleBillingService(Web3j web3j, Credentials credentials, TransactionManager txManager,
                          BlockchainProperties props, JdbcTemplate jdbc) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
        this.props = props;
        this.jdbc = jdbc;
    }

    public Map<String, Object> submitBillingReport(Long proyectoId, BigDecimal monto) {
        String contractAddress = props.getOracleBillingAddress();
        if (contractAddress == null || contractAddress.isBlank() || contractAddress.equals(ZERO_ADDRESS)) {
            throw new IllegalStateException("OracleBilling contract not configured");
        }

        // Scale monto to wei (1e18)
        BigInteger montoWei = monto.multiply(BigDecimal.TEN.pow(18)).toBigIntegerExact();

        // Unique txHash for this report: keccak256(proyectoId + monto + timestamp)
        byte[] hashInput = (proyectoId + "_" + monto.toPlainString() + "_" + System.currentTimeMillis()).getBytes();
        byte[] txHashBytes = org.web3j.crypto.Hash.sha3(hashInput);

        Function fn = new Function(
            "updateProjectBilling",
            List.of(new Uint256(proyectoId), new Uint256(montoWei), new Bytes32(txHashBytes)),
            List.of()
        );

        try {
            String encodedFunction = FunctionEncoder.encode(fn);
            EthSendTransaction response = txManager.sendTransaction(
                web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(200_000L),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (response.hasError()) {
                throw new RuntimeException("Error en tx: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("updateProjectBilling tx enviada: {} proyectoId={}", txHash, proyectoId);

            TransactionReceipt receipt = waitForReceipt(txHash);
            if (!"0x1".equals(receipt.getStatus())) {
                throw new RuntimeException("Tx revertida en el contrato: " + txHash);
            }

            jdbc.update("""
                INSERT INTO oracle_billing
                (proyecto_id, monto_facturado, fecha_reporte, oracle_address, tx_hash, procesado)
                VALUES (?, ?, NOW(), ?, ?, false)
                """,
                proyectoId,
                monto,
                contractAddress,
                txHash
            );

            Map<String, Object> result = new HashMap<>();
            result.put("proyectoId", proyectoId);
            result.put("montoFacturado", monto);
            result.put("oracleAddress", contractAddress);
            result.put("txHash", txHash);
            result.put("validado", true);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al enviar billing report: " + e.getMessage(), e);
        }
    }

    private TransactionReceipt waitForReceipt(String txHash) throws Exception {
        for (int i = 0; i < RECEIPT_POLL_ATTEMPTS; i++) {
            EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(txHash).send();
            Optional<TransactionReceipt> receipt = response.getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.get();
            }
            Thread.sleep(RECEIPT_POLL_INTERVAL_MS);
        }
        throw new RuntimeException("Tx no minada después de " + (RECEIPT_POLL_ATTEMPTS * RECEIPT_POLL_INTERVAL_MS / 1000) + "s: " + txHash);
    }
}
