package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.crypto.Credentials;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.systeam.config.BlockchainProperties;

@Service
public class OfferingContractService {

    private static final Logger log = LoggerFactory.getLogger(OfferingContractService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    public OfferingContractService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }

    public String registerOffering(
            BigInteger proyectoId, String creator,
            BigInteger softCap, BigInteger hardCap, BigInteger pricePerToken,
            BigInteger startTime, BigInteger endTime
    ) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "registerOffering",
            List.of(
                new Uint256(proyectoId),
                new Address(creator),
                new Uint256(softCap),
                new Uint256(hardCap),
                new Uint256(pricePerToken),
                new Uint256(startTime),
                new Uint256(endTime)
            ),
            List.of()
        );

        return sendTransaction(offeringAddress, fn, BigInteger.valueOf(500_000L));
    }

    public String invest(BigInteger proyectoId, BigInteger ideaAmount) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "invest",
            List.of(
                new Uint256(proyectoId),
                new Uint256(ideaAmount)
            ),
            List.of()
        );

        return sendTransaction(offeringAddress, fn, BigInteger.valueOf(300_000L));
    }

    public String finalize(BigInteger proyectoId) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "finalize",
            List.of(new Uint256(proyectoId)),
            List.of()
        );

        return sendTransaction(offeringAddress, fn, BigInteger.valueOf(200_000L));
    }

    public String claimTokens(BigInteger proyectoId) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "claimTokens",
            List.of(new Uint256(proyectoId)),
            List.of()
        );

        return sendTransaction(offeringAddress, fn, BigInteger.valueOf(200_000L));
    }

    public String refund(BigInteger proyectoId) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "refund",
            List.of(new Uint256(proyectoId)),
            List.of()
        );

        return sendTransaction(offeringAddress, fn, BigInteger.valueOf(200_000L));
    }

    public BigInteger getContribution(BigInteger proyectoId, String investor) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "getContribution",
            List.of(new Uint256(proyectoId), new Address(investor)),
            List.of(new TypeReference<Uint256>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = executeCall(offeringAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Uint256 val) {
            return val.getValue();
        }
        return BigInteger.ZERO;
    }

    public BigInteger getInvestorCount(BigInteger proyectoId) throws Exception {
        String offeringAddress = props.getOfferingContractAddress();
        assertConfigured(offeringAddress, "OfferingContract");

        Function fn = new Function(
            "getInvestorCount",
            List.of(new Uint256(proyectoId)),
            List.of(new TypeReference<Uint256>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = executeCall(offeringAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Uint256 val) {
            return val.getValue();
        }
        return BigInteger.ZERO;
    }

    private void assertConfigured(String address, String name) {
        if (address == null || address.equals("0x0000000000000000000000000000000000000000")) {
            throw new IllegalStateException(name + " no configurado");
        }
    }

    private String sendTransaction(String contractAddress, Function fn, BigInteger gasLimit) throws Exception {
        String encodedFunction = FunctionEncoder.encode(fn);
        EthSendTransaction response = txManager.sendTransaction(
            web3j.ethGasPrice().send().getGasPrice(),
            gasLimit,
            contractAddress,
            encodedFunction,
            BigInteger.ZERO
        );
        if (response.hasError()) {
            throw new RuntimeException("Error en tx: " + response.getError().getMessage());
        }
        String txHash = response.getTransactionHash();
        log.info("Tx enviada: {} contrato: {}", txHash, contractAddress);
        return txHash;
    }

    private List<org.web3j.abi.datatypes.Type> executeCall(String contractAddress, Function fn) throws Exception {
        String encodedFunction = FunctionEncoder.encode(fn);
        EthCall response = web3j.ethCall(
            Transaction.createEthCallTransaction(credentials.getAddress(), contractAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).send();
        if (response.hasError()) {
            throw new RuntimeException("Error en ethCall: " + response.getError().getMessage());
        }
        return FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
    }
}
