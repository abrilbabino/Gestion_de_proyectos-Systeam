package com.systeam.investment.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.config.BlockchainProperties;

@Service
public class SmartContractService {

    private static final Logger log = LoggerFactory.getLogger(SmartContractService.class);

    private final BlockchainService blockchain;
    private final BlockchainProperties props;
    private final InvestmentSwapService investmentSwapService;

    public SmartContractService(BlockchainService blockchain, BlockchainProperties props,
                                InvestmentSwapService investmentSwapService) {
        this.blockchain = blockchain;
        this.props = props;
        this.investmentSwapService = investmentSwapService;
    }

    public Map<String, Object> getContractInfo(String contractAddress) {
        Map<String, Object> info = new HashMap<>();
        try {
            info.put("address", contractAddress);
            BigInteger balance = blockchain.balanceOf(contractAddress, props.getTreasuryAddress());
            info.put("treasuryBalance", balance);
            info.put("backendAddress", blockchain.getBackendAddress());
            info.put("backendSepoliaEth", blockchain.getBalanceSepoliaInEth());
            BigInteger latestBlock = blockchain.getLatestBlock();
            info.put("latestBlock", latestBlock);
            info.put("note", "Conexion a Sepolia activa");
        } catch (Exception e) {
            log.error("Error al leer contrato {}: {}", contractAddress, e.getMessage());
            info.put("error", e.getMessage());
            info.put("note", "Error de conexion con Sepolia");
        }
        return info;
    }

    public Long getTotalSubTokens(String contractAddress) {
        try {
            BigInteger balance = blockchain.balanceOf(
                props.getProjectTokenAddress(),
                props.getTreasuryAddress()
            );
            return balance.longValue();
        } catch (Exception e) {
            log.error("Error getTotalSubTokens: {}", e.getMessage());
            return 0L;
        }
    }

    public Map<String, Long> getTokenDistribution(String contractAddress) {
        Map<String, Long> distribution = new HashMap<>();
        try {
            BigInteger totalSupply = blockchain.balanceOf(
                props.getProjectTokenAddress(),
                props.getProjectTokenAddress()
            );
            BigInteger treasuryBalance = blockchain.balanceOf(
                props.getProjectTokenAddress(),
                props.getTreasuryAddress()
            );
            distribution.put("totalSupply", totalSupply.longValue());
            distribution.put("treasuryBalance", treasuryBalance.longValue());
        } catch (Exception e) {
            log.error("Error getTokenDistribution: {}", e.getMessage());
            distribution.put("disponible", 0L);
            distribution.put("invertido", 0L);
        }
        return distribution;
    }

    public String deployContract(Long projectId, Long totalTokens, String tokenName) {
        try {
            String mintTx = blockchain.mintTokens(props.getTreasuryAddress(), BigInteger.valueOf(totalTokens));
            log.info("Deploy simulation - mint tx: {}", mintTx);
            return props.getProjectTokenAddress();
        } catch (Exception e) {
            log.error("Error deployContract: {}", e.getMessage());
            return "0x" + System.currentTimeMillis() + projectId;
        }
    }

    public Map<String, Object> recordInvestment(Long proyectoId, Long usuarioId, BigDecimal montoIdea, String txHash) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean txValida = blockchain.verifyTransaction(txHash);
            result.put("success", txValida);
            result.put("txHash", txHash);
            if (txValida) {
                BigInteger block = blockchain.getLatestBlock();
                result.put("blockNumber", block.longValue());
                result.put("note", "Transaccion verificada en Sepolia");

                BigInteger balance = blockchain.balanceOf(
                    props.getProjectTokenAddress(),
                    props.getTreasuryAddress()
                );
                result.put("treasuryTokensBalance", balance);
            } else {
                result.put("blockNumber", 0L);
                result.put("note", "Tx no encontrada o fallida en Sepolia");
            }
        } catch (Exception e) {
            log.error("Error recordInvestment: {}", e.getMessage());
            result.put("success", false);
            result.put("txHash", txHash);
            result.put("blockNumber", 0L);
            result.put("note", "Error de conexion con Sepolia: " + e.getMessage());
        }
        return result;
    }

    public boolean verifyTransaction(String txHash) {
        try {
            return blockchain.verifyTransaction(txHash);
        } catch (Exception e) {
            log.error("Error verifying tx {}: {}", txHash, e.getMessage());
            return false;
        }
    }

    public String getSenderFromTx(String txHash) {
        try {
            return blockchain.getSenderFromTx(txHash);
        } catch (Exception e) {
            log.error("Error getting sender from tx {}: {}", txHash, e.getMessage());
            return null;
        }
    }

    public boolean setGovernanceMultiplierOnChain(String walletAddress, int multiplier) {
        try {
            String txHash = blockchain.setGovernanceMultiplier(walletAddress, multiplier);
            log.info("setGovernanceMultiplier txHash: {}", txHash);
            return true;
        } catch (Exception e) {
            log.error("Error setting governance multiplier for {}: {}", walletAddress, e.getMessage());
            return false;
        }
    }

    public Map<String, Object> refundInvestment(Long proyectoId, Long usuarioId, BigDecimal montoIdea) {
        Map<String, Object> result = new HashMap<>();
        try {
            String refundTx = investmentSwapService.refund(
                proyectoId,
                montoIdea.multiply(BigDecimal.TEN.pow(18)).toBigInteger(),
                props.getTreasuryAddress(),
                props.getTreasuryAddress()
            );
            if (refundTx != null && blockchain.verifyTransaction(refundTx)) {
                result.put("success", true);
                result.put("refundTxHash", refundTx);
                result.put("note", "Reembolso on-chain verificado en InvestmentSwap");
            } else {
                log.warn("refundInvestment fallo en blockchain para proyecto {} usuario {}", proyectoId, usuarioId);
                result.put("success", false);
                result.put("refundTxHash", refundTx != null ? refundTx : "0x0");
                result.put("note", refundTx == null
                    ? "InvestmentSwap no disponible"
                    : "Transaccion de reembolso no encontrada o fallida en Sepolia");
            }
        } catch (Exception e) {
            log.error("Error refundInvestment: {}", e.getMessage());
            result.put("success", false);
            result.put("refundTxHash", "0x0");
            result.put("note", "Error en reembolso on-chain: " + e.getMessage());
        }
        return result;
    }

    private static final BigDecimal COSTO_BOOST_IDEA = new BigDecimal("100");
    private static final String BOOST_FEE_ADDRESS = "0x000000000000000000000000000000000000dEaD";

    public boolean verifyBoostTransfer(String txHash) {
        try {
            // Verify the transaction was successful on the blockchain
            return blockchain.verifyTransaction(txHash);
        } catch (Exception e) {
            log.error("Error verifyBoostTransfer: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> deployInvestmentContract(Long proyectoId) {
        Map<String, Object> result = new HashMap<>();
        result.put("contractAddress", props.getProjectTokenAddress());
        result.put("success", true);
        result.put("note", "Contrato ProjectToken ya deployado en Sepolia");
        return result;
    }
}
