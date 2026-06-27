package com.systeam.blockchain.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

@Service
public class SetBonusNFTService {

    private static final Logger log = LoggerFactory.getLogger(SetBonusNFTService.class);

    private final Web3j web3j;
    
    @Value("${blockchain.setbonus-nft-address:}")
    private String nftContractAddress;

    public SetBonusNFTService(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * Devuelve la rareza mas alta que posee una wallet.
     * 0 = Ninguno
     * 1 = Common
     * 2 = Rare
     * 3 = Epic
     * 4 = Legendary
     */
    public int getHighestRarity(String walletAddress) {
        if (nftContractAddress == null || nftContractAddress.isEmpty()) {
            return 0;
        }

        try {
            BigInteger balance = balanceOf(walletAddress);
            if (balance.compareTo(BigInteger.ZERO) == 0) {
                return 0;
            }

            int highest = 0;
            for (int i = 0; i < balance.intValue(); i++) {
                BigInteger tokenId = tokenOfOwnerByIndex(walletAddress, BigInteger.valueOf(i));
                int rarity = getPieceRarity(tokenId);
                // rarity enum in solidity: 0=Common, 1=Rare, 2=Epic, 3=Legendary
                // We add 1 so 0 means 'None'
                int adjustedRarity = rarity + 1; 
                if (adjustedRarity > highest) {
                    highest = adjustedRarity;
                }
            }
            return highest;

        } catch (Exception e) {
            log.error("Error reading SetBonusNFT for wallet {}: {}", walletAddress, e.getMessage());
            return 0;
        }
    }

    private BigInteger balanceOf(String walletAddress) throws Exception {
        Function function = new Function(
            "balanceOf",
            List.of(new Address(walletAddress)),
            List.of(new TypeReference<Uint256>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
            Transaction.createEthCallTransaction(null, nftContractAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).send();
        
        if (response.hasError()) {
            throw new RuntimeException("Error en balanceOf: " + response.getError().getMessage());
        }

        List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (results.isEmpty()) return BigInteger.ZERO;
        return (BigInteger) results.get(0).getValue();
    }

    private BigInteger tokenOfOwnerByIndex(String walletAddress, BigInteger index) throws Exception {
        Function function = new Function(
            "tokenOfOwnerByIndex",
            List.of(new Address(walletAddress), new Uint256(index)),
            List.of(new TypeReference<Uint256>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
            Transaction.createEthCallTransaction(null, nftContractAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).send();
        
        if (response.hasError()) {
            throw new RuntimeException("Error en tokenOfOwnerByIndex: " + response.getError().getMessage());
        }

        List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return (BigInteger) results.get(0).getValue();
    }

    private int getPieceRarity(BigInteger tokenId) throws Exception {
        Function function = new Function(
            "getPiece",
            List.of(new Uint256(tokenId)),
            List.of(
                new TypeReference<Uint256>() {}, // id
                new TypeReference<Uint8>() {},   // rarity
                new TypeReference<Uint256>() {}  // timestamp
            )
        );
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
            Transaction.createEthCallTransaction(null, nftContractAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).send();
        
        if (response.hasError()) {
            throw new RuntimeException("Error en getPiece: " + response.getError().getMessage());
        }

        List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (results.size() < 2) return 0;
        
        BigInteger rarityValue = (BigInteger) results.get(1).getValue();
        return rarityValue.intValue();
    }
    
    // DTO for Frontend
    public static class NFTDto {
        public BigInteger id;
        public String rarityName;
        public BigInteger timestamp;
        
        public NFTDto(BigInteger id, String rarityName, BigInteger timestamp) {
            this.id = id;
            this.rarityName = rarityName;
            this.timestamp = timestamp;
        }
    }

    public List<NFTDto> getWalletNFTs(String walletAddress) {
        List<NFTDto> nfts = new ArrayList<>();
        if (nftContractAddress == null || nftContractAddress.isEmpty()) {
            return nfts;
        }

        try {
            BigInteger balance = balanceOf(walletAddress);
            for (int i = 0; i < balance.intValue(); i++) {
                BigInteger tokenId = tokenOfOwnerByIndex(walletAddress, BigInteger.valueOf(i));
                
                // Decode piece
                Function function = new Function(
                    "getPiece",
                    List.of(new Uint256(tokenId)),
                    List.of(
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint8>() {},
                        new TypeReference<Uint256>() {}
                    )
                );
                String encodedFunction = FunctionEncoder.encode(function);
                EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, nftContractAddress, encodedFunction),
                    DefaultBlockParameterName.LATEST
                ).send();
                
                if (!response.hasError()) {
                    List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
                    if (results.size() >= 3) {
                        BigInteger id = (BigInteger) results.get(0).getValue();
                        int rarity = ((BigInteger) results.get(1).getValue()).intValue();
                        BigInteger timestamp = (BigInteger) results.get(2).getValue();
                        
                        String rarityName;
                        switch(rarity) {
                            case 0: rarityName = "Common"; break;
                            case 1: rarityName = "Rare"; break;
                            case 2: rarityName = "Epic"; break;
                            case 3: rarityName = "Legendary"; break;
                            default: rarityName = "Unknown";
                        }
                        
                        nfts.add(new NFTDto(id, rarityName, timestamp));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading wallet NFTs {}: {}", walletAddress, e.getMessage());
        }
        return nfts;
    }
}
