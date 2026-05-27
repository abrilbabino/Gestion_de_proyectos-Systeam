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
public class IdeaSwapService {

    private static final Logger log = LoggerFactory.getLogger(IdeaSwapService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    public IdeaSwapService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }

    public String swapUsdcForExactIdea(BigInteger ideaOut) throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "swapUsdcForExactIdea",
            List.of(new Uint256(ideaOut)),
            List.of()
        );
        return sendTransaction(swapAddress, fn, BigInteger.valueOf(300_000L));
    }

    public String swapIdeaForExactUsdc(BigInteger usdcOut) throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "swapIdeaForExactUsdc",
            List.of(new Uint256(usdcOut)),
            List.of()
        );
        return sendTransaction(swapAddress, fn, BigInteger.valueOf(300_000L));
    }

    public String addLiquidity(BigInteger ideaAmount, BigInteger usdcAmount) throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "addLiquidity",
            List.of(new Uint256(ideaAmount), new Uint256(usdcAmount)),
            List.of()
        );
        return sendTransaction(swapAddress, fn, BigInteger.valueOf(400_000L));
    }

    public String removeLiquidity(BigInteger ideaAmount, BigInteger usdcAmount) throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "removeLiquidity",
            List.of(new Uint256(ideaAmount), new Uint256(usdcAmount)),
            List.of()
        );
        return sendTransaction(swapAddress, fn, BigInteger.valueOf(400_000L));
    }

    public BigInteger getIdeaReserve() throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "ideaReserve",
            List.of(),
            List.of(new TypeReference<Uint256>() {})
        );
        return extractUint256(executeCall(swapAddress, fn));
    }

    public BigInteger getUsdcReserve() throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        assertConfigured(swapAddress, "IdeaSwap");

        Function fn = new Function(
            "usdcReserve",
            List.of(),
            List.of(new TypeReference<Uint256>() {})
        );
        return extractUint256(executeCall(swapAddress, fn));
    }

    public BigInteger getPrice() throws Exception {
        BigInteger ideaReserve = getIdeaReserve();
        BigInteger usdcReserve = getUsdcReserve();
        if (ideaReserve.equals(BigInteger.ZERO)) return BigInteger.ZERO;
        return usdcReserve.multiply(BigInteger.valueOf(10_000)).divide(ideaReserve);
    }

    private BigInteger extractUint256(List<org.web3j.abi.datatypes.Type> result) {
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
