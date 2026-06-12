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
import org.web3j.crypto.Hash;
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
public class IdeafyFactoryService {

    private static final Logger log = LoggerFactory.getLogger(IdeafyFactoryService.class);
    static final String PROJECT_LAUNCHED_TOPIC = Hash.sha3String(
        "ProjectLaunched(uint256,address,address,uint256,uint256,uint256)"
    );

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    @org.springframework.beans.factory.annotation.Autowired
    public IdeafyFactoryService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props);
    }

    IdeafyFactoryService(Web3j web3j, Credentials credentials, TransactionManager txManager,
                          BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
        this.props = props;
    }

    public String launchProject(
            Long proyectoId, Integer rubroId, Integer dividendBps,
            String creator, String nombre, String simbolo, BigInteger supplyInicial
    ) throws Exception {
        String factoryAddress = props.getIdeafyFactoryAddress();
        assertConfigured(factoryAddress, "IdeafyFactory");

        Function fn = new Function(
            "launchProject",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Uint256(BigInteger.valueOf(rubroId)),
                new Uint256(BigInteger.valueOf(dividendBps)),
                new Address(creator),
                new Utf8String(nombre),
                new Utf8String(simbolo),
                new Uint256(supplyInicial)
            ),
            List.of()
        );

        String txHash = sendTransaction(factoryAddress, fn, BigInteger.valueOf(800_000L));
        return decodeTokenAddressFromLogs(txHash);
    }

    public String allocateTokens(Long proyectoId, String toAddress, BigInteger amount) throws Exception {
        String factoryAddress = props.getIdeafyFactoryAddress();
        assertConfigured(factoryAddress, "IdeafyFactory");

        Function fn = new Function(
            "allocateTokens",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Address(toAddress),
                new Uint256(amount)
            ),
            List.of()
        );

        return sendTransaction(factoryAddress, fn, BigInteger.valueOf(150_000L));
    }

    public String obtenerTokenDeProyecto(Long proyectoId) throws Exception {
        String factoryAddress = props.getIdeafyFactoryAddress();
        assertConfigured(factoryAddress, "IdeafyFactory");

        Function fn = new Function(
            "subTokenOfProject",
            List.of(new Uint256(BigInteger.valueOf(proyectoId))),
            List.of(new TypeReference<Address>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = executeCall(factoryAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Address addr) {
            String tokenAddr = addr.getValue();
            if (!"0x0000000000000000000000000000000000000000".equals(tokenAddr)) {
                return tokenAddr;
            }
        }
        return null;
    }

    public BigInteger getSubTokenCount() throws Exception {
        String factoryAddress = props.getIdeafyFactoryAddress();
        assertConfigured(factoryAddress, "IdeafyFactory");

        Function fn = new Function(
            "getSubTokenCount",
            List.of(),
            List.of(new TypeReference<Uint256>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = executeCall(factoryAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Uint256 count) {
            return count.getValue();
        }
        return BigInteger.ZERO;
    }

    public List<org.web3j.abi.datatypes.Type> getSubTokensPaginated(BigInteger offset, BigInteger limit) throws Exception {
        String factoryAddress = props.getIdeafyFactoryAddress();
        assertConfigured(factoryAddress, "IdeafyFactory");

        Function fn = new Function(
            "getSubTokensPaginated",
            List.of(new Uint256(offset), new Uint256(limit)),
            List.of(
                new TypeReference<org.web3j.abi.datatypes.DynamicArray<Address>>() {},
                new TypeReference<Uint256>() {}
            )
        );

        return executeCall(factoryAddress, fn);
    }

    private String decodeTokenAddressFromLogs(String txHash) throws Exception {
        var receipt = web3j.ethGetTransactionReceipt(txHash).send();
        if (receipt.getTransactionReceipt().isEmpty()) {
            throw new RuntimeException("No receipt for tx " + txHash);
        }

        var logs = receipt.getTransactionReceipt().get().getLogs();
        for (var logEntry : logs) {
            if (logEntry.getTopics().size() >= 3) {
                String topic0 = logEntry.getTopics().get(0);
                if (PROJECT_LAUNCHED_TOPIC.equals(topic0)) {
                    String tokenAddrHex = logEntry.getTopics().get(2);
                    if (tokenAddrHex != null && tokenAddrHex.length() >= 40) {
                        return "0x" + tokenAddrHex.substring(tokenAddrHex.length() - 40);
                    }
                }
            }
        }
        throw new RuntimeException("ProjectLaunched event not found in tx " + txHash);
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
