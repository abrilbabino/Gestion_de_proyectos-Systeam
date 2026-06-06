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
import org.web3j.abi.datatypes.Utf8String;
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
public class InvestmentSwapService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentSwapService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final TransactionManager txManager;
    private final BlockchainProperties props;

    @org.springframework.beans.factory.annotation.Autowired
    public InvestmentSwapService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props);
    }

    InvestmentSwapService(Web3j web3j, Credentials credentials, TransactionManager txManager, BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
        this.props = props;
    }

    public String crearTokenProyecto(
            Long proyectoId, String nombre, String simbolo, BigInteger supplyInicial
    ) throws Exception {
        String swapAddress = props.getInvestmentSwapAddress();

        if (swapAddress == null || swapAddress.equals("0x0000000000000000000000000000000000000000")) {
            log.warn("InvestmentSwap no configurado. Usando ProjectToken global como fallback.");
            return props.getProjectTokenAddress();
        }

        Function fn = new Function(
            "crearTokenProyecto",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Utf8String(nombre),
                new Utf8String(simbolo),
                new Uint256(supplyInicial)
            ),
            List.of(new TypeReference<Address>() {})
        );

        String encodedFunction = FunctionEncoder.encode(fn);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice,
            BigInteger.valueOf(500_000L),
            swapAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Error al crear token via InvestmentSwap: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        log.info("Token creado via InvestmentSwap. Tx: {}", txHash);

        return decodeTokenAddress(txHash, fn);
    }

    public String invest(
            Long proyectoId, BigInteger ideaAmount, BigInteger subTokenAmount, String investor
    ) throws Exception {
        String swapAddress = props.getInvestmentSwapAddress();

        if (swapAddress == null || swapAddress.equals("0x0000000000000000000000000000000000000000")) {
            log.warn("InvestmentSwap no configurado. Simulando swap en modo offline.");
            return "0xswap-offline-" + System.currentTimeMillis();
        }

        Function fn = new Function(
            "invest",
            List.of(
                new Uint256(BigInteger.valueOf(proyectoId)),
                new Uint256(ideaAmount),
                new Uint256(subTokenAmount),
                new Address(investor)
            ),
            List.of()
        );

        String encodedFunction = FunctionEncoder.encode(fn);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        EthSendTransaction response = txManager.sendTransaction(
            gasPrice,
            BigInteger.valueOf(300_000L),
            swapAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Error en swap InvestmentSwap: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        log.info("Swap ejecutado: proyecto={}, ideaAmount={}, subTokens={}, investor={}, tx={}",
            proyectoId, ideaAmount, subTokenAmount, investor, txHash);

        return txHash;
    }

    public String refund(
            Long proyectoId, BigInteger subTokenAmount, String holder, String investor
    ) {
        String swapAddress = props.getInvestmentSwapAddress();
        if (swapAddress == null || swapAddress.equals("0x0000000000000000000000000000000000000000")) {
            log.warn("InvestmentSwap no configurado. Reembolso simulado (offline).");
            return "0xrefund-offline-" + System.currentTimeMillis();
        }

        try {
            Function fn = new Function(
                "refund",
                List.of(
                    new Uint256(BigInteger.valueOf(proyectoId)),
                    new Uint256(subTokenAmount),
                    new Address(holder),
                    new Address(investor)
                ),
                List.of()
            );

            String encodedFunction = FunctionEncoder.encode(fn);
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            EthSendTransaction response = txManager.sendTransaction(
                gasPrice,
                BigInteger.valueOf(300_000L),
                swapAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (response.hasError()) {
                log.error("Error en refund on-chain: {}", response.getError().getMessage());
                return null;
            }

            String txHash = response.getTransactionHash();
            log.info("Refund on-chain exitoso: proyecto={}, subTokens={}, tx={}",
                proyectoId, subTokenAmount, txHash);
            return txHash;

        } catch (Exception e) {
            log.error("Excepción en refund on-chain: {}", e.getMessage());
            return null;
        }
    }

    public String obtenerTokenDeProyecto(Long proyectoId) throws Exception {
        String swapAddress = props.getInvestmentSwapAddress();
        if (swapAddress == null || swapAddress.equals("0x0000000000000000000000000000000000000000")) {
            return props.getProjectTokenAddress();
        }

        Function fn = new Function(
            "obtenerTokenDeProyecto",
            List.of(new Uint256(BigInteger.valueOf(proyectoId))),
            List.of(new TypeReference<Address>() {})
        );

        String encodedFunction = FunctionEncoder.encode(fn);
        EthCall response = web3j.ethCall(
            Transaction.createEthCallTransaction(
                credentials.getAddress(), swapAddress, encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).send();

        if (response.hasError()) {
            throw new RuntimeException("Error al consultar tokenDeProyecto: " + response.getError().getMessage());
        }

        List<org.web3j.abi.datatypes.Type> result =
            FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());

        if (!result.isEmpty() && result.get(0) instanceof Address addr) {
            String tokenAddr = addr.getValue();
            if (!"0x0000000000000000000000000000000000000000".equals(tokenAddr)) {
                return tokenAddr;
            }
        }

        return props.getProjectTokenAddress();
    }

    private String decodeTokenAddress(String txHash, Function fn) throws Exception {
        var receipt = web3j.ethGetTransactionReceipt(txHash).send();
        if (receipt.getTransactionReceipt().isEmpty()) {
            log.warn("No se encontró receipt para tx {}. Usando fallback.", txHash);
            return props.getProjectTokenAddress();
        }

        var logs = receipt.getTransactionReceipt().get().getLogs();
        if (logs.isEmpty()) {
            log.warn("No hay logs en tx {}. Usando fallback.", txHash);
            return props.getProjectTokenAddress();
        }

        String eventData = logs.get(0).getData();
        if (eventData == null || eventData.equals("0x")) {
            log.warn("Event data vacío en tx {}. Usando fallback.", txHash);
            return props.getProjectTokenAddress();
        }

        List<org.web3j.abi.datatypes.Type> decoded =
            FunctionReturnDecoder.decode(eventData, fn.getOutputParameters());

        if (!decoded.isEmpty() && decoded.get(0) instanceof Address addr) {
            return addr.getValue();
        }

        return props.getProjectTokenAddress();
    }
}
