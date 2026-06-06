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
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
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
public class IdeaGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(IdeaGovernanceService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    @org.springframework.beans.factory.annotation.Autowired
    public IdeaGovernanceService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props);
    }

    IdeaGovernanceService(Web3j web3j, Credentials credentials, TransactionManager txManager,
                           BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
        this.props = props;
    }

    public String createProposal(String description, Integer proposalType, byte[] data) throws Exception {
        String governanceAddress = props.getIdeaGovernanceAddress();
        assertConfigured(governanceAddress, "IdeaGovernance");

        Function fn = new Function(
            "createProposal",
            List.of(
                new Utf8String(description),
                new Uint8(proposalType),
                new org.web3j.abi.datatypes.DynamicBytes(data)
            ),
            List.of()
        );
        return sendTransaction(governanceAddress, fn, BigInteger.valueOf(400_000L));
    }

    public String vote(BigInteger proposalId, Boolean support) throws Exception {
        String governanceAddress = props.getIdeaGovernanceAddress();
        assertConfigured(governanceAddress, "IdeaGovernance");

        Function fn = new Function(
            "vote",
            List.of(new Uint256(proposalId), new org.web3j.abi.datatypes.Bool(support)),
            List.of()
        );
        return sendTransaction(governanceAddress, fn, BigInteger.valueOf(200_000L));
    }

    public String executeProposal(BigInteger proposalId) throws Exception {
        String governanceAddress = props.getIdeaGovernanceAddress();
        assertConfigured(governanceAddress, "IdeaGovernance");

        Function fn = new Function(
            "executeProposal",
            List.of(new Uint256(proposalId)),
            List.of()
        );
        return sendTransaction(governanceAddress, fn, BigInteger.valueOf(200_000L));
    }

    public BigInteger getProposalCount() throws Exception {
        String governanceAddress = props.getIdeaGovernanceAddress();
        assertConfigured(governanceAddress, "IdeaGovernance");

        // proposalCounter es una variable public de Solidity → getter automático
        Function fn = new Function(
            "proposalCounter",
            List.of(),
            List.of(new TypeReference<Uint256>() {})
        );
        List<org.web3j.abi.datatypes.Type> result = executeCall(governanceAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Uint256 val) {
            return val.getValue();
        }
        return BigInteger.ZERO;
    }

    public List<org.web3j.abi.datatypes.Type> getProposal(BigInteger proposalId) throws Exception {
        String governanceAddress = props.getIdeaGovernanceAddress();
        assertConfigured(governanceAddress, "IdeaGovernance");

        // Retorna Proposal struct como tuple: (id, proposer, description, proposalType, data,
        //                                       startTime, endTime, forVotes, againstVotes, executed)
        Function fn = new Function(
            "getProposal",
            List.of(new Uint256(proposalId)),
            List.of(
                new TypeReference<Uint256>() {},
                new TypeReference<Address>() {},
                new TypeReference<Utf8String>() {},
                new TypeReference<Uint8>() {},
                new TypeReference<org.web3j.abi.datatypes.DynamicBytes>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<org.web3j.abi.datatypes.Bool>() {}
            )
        );
        return executeCall(governanceAddress, fn);
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
