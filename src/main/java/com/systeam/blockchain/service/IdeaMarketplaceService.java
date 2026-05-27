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
public class IdeaMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(IdeaMarketplaceService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    public IdeaMarketplaceService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = new RawTransactionManager(web3j, credentials);
        this.props = props;
    }

    public String listTokens(String subToken, BigInteger amount, BigInteger price) throws Exception {
        String marketplaceAddress = props.getIdeaMarketplaceAddress();
        assertConfigured(marketplaceAddress, "IdeaMarketplace");

        Function fn = new Function(
            "listTokens",
            List.of(new Address(subToken), new Uint256(amount), new Uint256(price)),
            List.of()
        );
        return sendTransaction(marketplaceAddress, fn, BigInteger.valueOf(300_000L));
    }

    public String buyTokens(BigInteger listingId, BigInteger amount) throws Exception {
        String marketplaceAddress = props.getIdeaMarketplaceAddress();
        assertConfigured(marketplaceAddress, "IdeaMarketplace");

        Function fn = new Function(
            "buyTokens",
            List.of(new Uint256(listingId), new Uint256(amount)),
            List.of()
        );
        return sendTransaction(marketplaceAddress, fn, BigInteger.valueOf(250_000L));
    }

    public String cancelListing(BigInteger listingId) throws Exception {
        String marketplaceAddress = props.getIdeaMarketplaceAddress();
        assertConfigured(marketplaceAddress, "IdeaMarketplace");

        Function fn = new Function(
            "cancelListing",
            List.of(new Uint256(listingId)),
            List.of()
        );
        return sendTransaction(marketplaceAddress, fn, BigInteger.valueOf(150_000L));
    }

    public String collectFees() throws Exception {
        String marketplaceAddress = props.getIdeaMarketplaceAddress();
        assertConfigured(marketplaceAddress, "IdeaMarketplace");

        Function fn = new Function(
            "collectFees",
            List.of(),
            List.of()
        );
        return sendTransaction(marketplaceAddress, fn, BigInteger.valueOf(100_000L));
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
}
