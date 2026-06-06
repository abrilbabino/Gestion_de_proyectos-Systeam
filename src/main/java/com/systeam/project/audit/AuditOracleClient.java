package com.systeam.project.audit;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;
import com.systeam.project.audit.dto.ResultadoAuditoria;

/**
 * Thin client that submits audit findings to the on-chain oracle contract.
 * Degrades gracefully: returns null on zero-address, timeout, or any exception.
 */
@Service
public class AuditOracleClient {

    private static final Logger log = LoggerFactory.getLogger(AuditOracleClient.class);
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final int RECEIPT_POLL_ATTEMPTS = 40;
    private static final long RECEIPT_POLL_INTERVAL_MS = 3_000L;

    private final Web3j web3j;
    private final Credentials credentials;
    private final BlockchainProperties props;

    public AuditOracleClient(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.props = props;
    }

    /**
     * Submits an audit finding to the oracle contract.
     *
     * @return the transaction hash on success, or {@code null} on degradation (zero-address, timeout, error)
     */
    public String submitAuditFinding(Long proyectoId,
                                     ResultadoAuditoria resultado,
                                     String observaciones,
                                     String kybUrl) {
        String contractAddress = props.getOracleBillingAddress();

        if (contractAddress == null
                || contractAddress.isBlank()
                || ZERO_ADDRESS.equalsIgnoreCase(contractAddress)) {
            log.debug("AuditOracleClient: zero-address configured — skipping on-chain call");
            return null;
        }

        try {
            // Encode txHash as keccak256(proyectoId || resultado || kybUrl || timestamp)
            long timestamp = System.currentTimeMillis();
            byte[] hashInput = (proyectoId + "_" + resultado.name() + "_" + kybUrl + "_" + timestamp)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] txHashBytes = Hash.sha3(hashInput);

            // uint8: 0 = APROBADO, 1 = RECHAZADO
            int resultadoUint8 = (resultado == ResultadoAuditoria.APROBADO) ? 0 : 1;

            // ABI: submitAuditFinding(uint256 proyectoId, uint8 resultado, string calldata observaciones, bytes32 txHash)
            Function fn = new Function(
                "submitAuditFinding",
                List.of(
                    new Uint256(BigInteger.valueOf(proyectoId)),
                    new Uint8(BigInteger.valueOf(resultadoUint8)),
                    new Utf8String(observaciones != null ? observaciones : ""),
                    new Bytes32(txHashBytes)
                ),
                List.of()
            );

            TransactionManager txManager = new RawTransactionManager(web3j, credentials);
            String encodedFunction = FunctionEncoder.encode(fn);

            EthSendTransaction response = txManager.sendTransaction(
                web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(200_000L),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (response.hasError()) {
                log.warn("AuditOracleClient: RPC error — {} (proyectoId={})",
                    response.getError().getMessage(), proyectoId);
                return null;
            }

            String txHash = response.getTransactionHash();
            log.info("AuditOracleClient: submitAuditFinding tx sent — {} (proyectoId={})", txHash, proyectoId);

            TransactionReceipt receipt = waitForReceipt(txHash);
            if (!"0x1".equals(receipt.getStatus())) {
                log.warn("AuditOracleClient: tx reverted on-chain — {} (proyectoId={})", txHash, proyectoId);
                return null;
            }

            return txHash;

        } catch (Exception e) {
            log.warn("AuditOracleClient: degraded — {} (proyectoId={}). Finding will be saved with tx_hash=null.",
                e.getMessage(), proyectoId);
            return null;
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
        throw new RuntimeException("Tx not mined after "
            + (RECEIPT_POLL_ATTEMPTS * RECEIPT_POLL_INTERVAL_MS / 1000) + "s: " + txHash);
    }
}
