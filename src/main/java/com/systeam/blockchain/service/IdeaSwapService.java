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

    @org.springframework.beans.factory.annotation.Autowired
    public IdeaSwapService(Web3j web3j, Credentials credentials, BlockchainProperties props) {
        this(web3j, credentials, new RawTransactionManager(web3j, credentials), props);
    }

    IdeaSwapService(Web3j web3j, Credentials credentials, TransactionManager txManager,
                     BlockchainProperties props) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.txManager = txManager;
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

    /**
     * Igual que swapUsdcForExactIdea pero:
     * 1. Verifica y aprueba el allowance de USDC al contrato IdeaSwap si hace falta
     * 2. Espera a que el swap sea minado antes de retornar
     * Necesario para evitar el error de "in-flight transaction limit"
     * cuando se llama a distribute() inmediatamente después.
     */
    public String swapUsdcForExactIdeaAndWait(BigInteger ideaOut) throws Exception {
        String swapAddress = props.getIdeaSwapAddress();
        String usdcAddress = props.getUsdcAddress();
        assertConfigured(usdcAddress, "USDC");

        // 1. Calcular cuanto USDC necesitamos (aproximado: ideaOut * usdcReserve / ideaReserve)
        BigInteger ideaReserve = getIdeaReserve();
        BigInteger usdcReserve = getUsdcReserve();
        BigInteger usdcNeeded = ideaOut.multiply(usdcReserve).divide(ideaReserve)
            .multiply(BigInteger.valueOf(10100)).divide(BigInteger.valueOf(10000)); // +1% buffer

        // 2. Verificar allowance de USDC
        Function allowanceFn = new Function(
            "allowance",
            List.of(new Address(credentials.getAddress()), new Address(swapAddress)),
            List.of(new TypeReference<Uint256>() {})
        );
        List<org.web3j.abi.datatypes.Type> allowanceResult = executeCall(usdcAddress, allowanceFn);
        BigInteger currentAllowance = BigInteger.ZERO;
        if (!allowanceResult.isEmpty() && allowanceResult.get(0) instanceof Uint256 val) {
            currentAllowance = val.getValue();
        }

        // 3. Aprobar USDC si hace falta (aprobacion infinita)
        if (currentAllowance.compareTo(usdcNeeded) < 0) {
            BigInteger maxUint256 = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
            Function approveFn = new Function(
                "approve",
                List.of(new Address(swapAddress), new Uint256(maxUint256)),
                List.of()
            );
            String approveTxHash = sendTransaction(usdcAddress, approveFn, BigInteger.valueOf(100_000L));
            log.info("Aprobando USDC para IdeaSwap. Tx: {}", approveTxHash);
            waitForMining(approveTxHash, 12, 5_000);
            log.info("USDC aprobado correctamente para IdeaSwap");
        }

        // 4. Ejecutar el swap y esperar confirmacion
        String txHash = swapUsdcForExactIdea(ideaOut);
        waitForMining(txHash, 12, 5_000);
        return txHash;
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

    public BigInteger getIdeaOutForExactUsdcIn(BigInteger usdcIn) throws Exception {
        BigInteger ideaReserve = getIdeaReserve();
        BigInteger usdcReserve = getUsdcReserve();
        if (ideaReserve.equals(BigInteger.ZERO) || usdcReserve.equals(BigInteger.ZERO)) {
            throw new IllegalStateException("Pool de liquidez vacío");
        }

        // Recuperar el fee (feeBps es 5 por defecto). Lo leemos del contrato o lo harcodeamos.
        // En IdeaSwap.sol, feeBps es public, pero asumimos 5 (0.05%) para simplificar la cuenta off-chain
        // o lo extraemos con una call, pero usaremos 5 directo.
        BigInteger feeBps = BigInteger.valueOf(5); 

        // Fórmula AMM constante: usdcInNet = usdcIn * (10000 - feeBps) / 10000
        // ideaOut = (usdcInNet * ideaReserve) / (usdcReserve + usdcInNet)
        // NOTA: IdeaSwap.sol implementa precio lineal para simplificar MVP:
        // usdcIn = (ideaOut * usdcReserve) / ideaReserve
        // Entonces: ideaOut = (usdcInNet * ideaReserve) / usdcReserve

        // Calculamos el usdcNeto (descontando la comisión que cobra el Swap)
        // usdcInTotal = usdcIn + fee => usdcIn = usdcInTotal / (1 + feeBps/10000)
        // usdcIn = usdcInTotal * 10000 / (10000 + feeBps)
        BigInteger usdcInNet = usdcIn.multiply(BigInteger.valueOf(10000)).divide(BigInteger.valueOf(10000).add(feeBps));
        
        return usdcInNet.multiply(ideaReserve).divide(usdcReserve);
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

    /**
     * Espera a que una transacción sea incluida en un bloque.
     * Hace polling cada intervalMs hasta maxAttempts veces.
     * Lanza excepción si el timeout se agota o la tx falla.
     */
    private void waitForMining(String txHash, int maxAttempts, long intervalMs) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            var receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                String status = receipt.get().getStatus();
                if ("0x1".equals(status)) {
                    log.info("Tx {} minada exitosamente en bloque {}", txHash, receipt.get().getBlockNumber());
                    return;
                } else {
                    throw new RuntimeException("La tx " + txHash + " fue minada pero revertida (status=" + status + ")");
                }
            }
            log.info("Esperando confirmación de tx {} (intento {}/{})", txHash, i + 1, maxAttempts);
            Thread.sleep(intervalMs);
        }
        throw new RuntimeException("Timeout esperando la tx " + txHash + " tras " + (maxAttempts * intervalMs / 1000) + "s");
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
