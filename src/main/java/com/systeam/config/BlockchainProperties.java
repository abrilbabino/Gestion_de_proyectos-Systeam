package com.systeam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    private String rpcUrl;
    private String paymentGatewayAddress;
    private String projectTokenAddress;
    private String usdcAddress;
    private String treasuryAddress;
    private String privateKey;
    private String tokenFactoryAddress;
    private String investmentSwapAddress;

    public String getRpcUrl() { return rpcUrl; }
    public void setRpcUrl(String rpcUrl) { this.rpcUrl = rpcUrl; }

    public String getPaymentGatewayAddress() { return paymentGatewayAddress; }
    public void setPaymentGatewayAddress(String paymentGatewayAddress) { this.paymentGatewayAddress = paymentGatewayAddress; }

    public String getProjectTokenAddress() { return projectTokenAddress; }
    public void setProjectTokenAddress(String projectTokenAddress) { this.projectTokenAddress = projectTokenAddress; }

    public String getUsdcAddress() { return usdcAddress; }
    public void setUsdcAddress(String usdcAddress) { this.usdcAddress = usdcAddress; }

    public String getTreasuryAddress() { return treasuryAddress; }
    public void setTreasuryAddress(String treasuryAddress) { this.treasuryAddress = treasuryAddress; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getTokenFactoryAddress() { return tokenFactoryAddress; }
    public void setTokenFactoryAddress(String tokenFactoryAddress) { this.tokenFactoryAddress = tokenFactoryAddress; }

    public String getInvestmentSwapAddress() { return investmentSwapAddress; }
    public void setInvestmentSwapAddress(String investmentSwapAddress) { this.investmentSwapAddress = investmentSwapAddress; }
}
