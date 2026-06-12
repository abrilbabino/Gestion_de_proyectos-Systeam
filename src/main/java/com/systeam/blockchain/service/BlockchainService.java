package com.systeam.blockchain.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.web3j.abi.datatypes.generated.Bytes32;

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
import org.web3j.utils.Convert;

import com.systeam.config.BlockchainProperties;

@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    @org.springframework.beans.factory.annotation.Autowired
    public BlockchainService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props);
    }

    BlockchainService(Web3j web3j, Credentials credentials, TransactionManager txManager,
                       BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
        this.props = props;
    }

    public BigInteger balanceOf(String tokenAddress, String ownerAddress) throws Exception {
        Function fn = new Function(
            "balanceOf",
            List.of(new Address(ownerAddress)),
            List.of(new TypeReference<Uint256>() {})
        );
        return callAndExtractUint256(tokenAddress, fn);
    }

    public BigInteger allowance(String tokenAddress, String owner, String spender) throws Exception {
        Function fn = new Function(
            "allowance",
            List.of(new Address(owner), new Address(spender)),
            List.of(new TypeReference<Uint256>() {})
        );
        return callAndExtractUint256(tokenAddress, fn);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public String getContractOwner(String tokenAddress) throws Exception {
        Function fn = new Function(
            "owner",
            List.of(),
            List.of(new TypeReference<Address>() {})
        );
        List<org.web3j.abi.datatypes.Type> result = executeCall(tokenAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Address addr) {
            return addr.getValue();
        }
        return null;
    }

    public BigInteger getLatestBlock() throws Exception {
        return web3j.ethBlockNumber().send().getBlockNumber();
    }

    public String mintTokens(String toAddress, BigInteger amountWei) throws Exception {
        Function fn = new Function(
            "mint",
            List.of(new Address(toAddress), new Uint256(amountWei)),
            List.of()
        );
        return executeWrite(props.getProjectTokenAddress(), fn);
    }

    public String burnTokens(String fromAddress, BigInteger amountWei) throws Exception {
        Function fn = new Function(
            "burnFrom",
            List.of(new Address(fromAddress), new Uint256(amountWei)),
            List.of()
        );
        return executeWrite(props.getProjectTokenAddress(), fn);
    }

    public String transferIdea(String to, BigInteger amountWei) throws Exception {
        Function fn = new Function(
            "transfer",
            List.of(new Address(to), new Uint256(amountWei)),
            List.of()
        );
        return executeWrite(props.getIdeaTokenAddress(), fn);
    }

    public String payUSDC(BigInteger amountInUSDC, String actionId) throws Exception {
        byte[] padded = new byte[32];
        byte[] raw = actionId.getBytes();
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, 32));
        Function fn = new Function(
            "pay",
            List.of(new Uint256(amountInUSDC), new Bytes32(padded)),
            List.of()
        );
        return executeWrite(props.getPaymentGatewayAddress(), fn);
    }

    public boolean verifyTransaction(String txHash) throws Exception {
        for (int i = 0; i < 15; i++) {
            var receipt = web3j.ethGetTransactionReceipt(txHash).send();
            if (receipt.getTransactionReceipt().isPresent()) {
                return receipt.getTransactionReceipt().get().getStatus().equals("0x1");
            }
            if (i < 14) Thread.sleep(2000);
        }
        return false;
    }

    public String getSenderFromTx(String txHash) throws Exception {
        var ethTx = web3j.ethGetTransactionByHash(txHash).send();
        if (ethTx.getTransaction().isPresent()) {
            return ethTx.getTransaction().get().getFrom();
        }
        return null;
    }

    public String getBackendAddress() {
        return credentials.getAddress();
    }

    public BigInteger getBalanceSepolia() throws Exception {
        return web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST)
            .send().getBalance();
    }

    public BigDecimal getBalanceSepoliaInEth() throws Exception {
        BigInteger wei = getBalanceSepolia();
        return Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
    }

    @SuppressWarnings("rawtypes")
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

    @SuppressWarnings("rawtypes")
    private BigInteger callAndExtractUint256(String contractAddress, Function fn) throws Exception {
        List<org.web3j.abi.datatypes.Type> result = executeCall(contractAddress, fn);
        if (!result.isEmpty() && result.get(0) instanceof Uint256 uintVal) {
            return uintVal.getValue();
        }
        return BigInteger.ZERO;
    }

    private String executeWrite(String contractAddress, Function fn) throws Exception {
        String encodedFunction = FunctionEncoder.encode(fn);

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger nonce = web3j.ethGetTransactionCount(
            credentials.getAddress(), DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice,        
            BigInteger.valueOf(300_000L),  // gas limit
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
}
