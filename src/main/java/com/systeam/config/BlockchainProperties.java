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
    private String ideaTokenAddress;
    private String ideafyFactoryAddress;
    private String offeringContractAddress;
    private String dividendDistributorAddress;
    private String ideaSwapAddress;
    private String ideaMarketplaceAddress;
    private String ideaGovernanceAddress;
    private String oracleBillingAddress;
    private String auditOracleAddress;
    private int eventPollInterval;
    private int eventReconcileInterval;

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

    public String getIdeaTokenAddress() { return ideaTokenAddress; }
    public void setIdeaTokenAddress(String ideaTokenAddress) { this.ideaTokenAddress = ideaTokenAddress; }

    public String getIdeafyFactoryAddress() { return ideafyFactoryAddress; }
    public void setIdeafyFactoryAddress(String ideafyFactoryAddress) { this.ideafyFactoryAddress = ideafyFactoryAddress; }

    public String getOfferingContractAddress() { return offeringContractAddress; }
    public void setOfferingContractAddress(String offeringContractAddress) { this.offeringContractAddress = offeringContractAddress; }

    public String getDividendDistributorAddress() { return dividendDistributorAddress; }
    public void setDividendDistributorAddress(String dividendDistributorAddress) { this.dividendDistributorAddress = dividendDistributorAddress; }

    public String getIdeaSwapAddress() { return ideaSwapAddress; }
    public void setIdeaSwapAddress(String ideaSwapAddress) { this.ideaSwapAddress = ideaSwapAddress; }

    public String getIdeaMarketplaceAddress() { return ideaMarketplaceAddress; }
    public void setIdeaMarketplaceAddress(String ideaMarketplaceAddress) { this.ideaMarketplaceAddress = ideaMarketplaceAddress; }

    public String getIdeaGovernanceAddress() { return ideaGovernanceAddress; }
    public void setIdeaGovernanceAddress(String ideaGovernanceAddress) { this.ideaGovernanceAddress = ideaGovernanceAddress; }

    public String getOracleBillingAddress() { return oracleBillingAddress; }
    public void setOracleBillingAddress(String oracleBillingAddress) { this.oracleBillingAddress = oracleBillingAddress; }

    public String getAuditOracleAddress() { return auditOracleAddress; }
    public void setAuditOracleAddress(String auditOracleAddress) { this.auditOracleAddress = auditOracleAddress; }

    public int getEventPollInterval() { return eventPollInterval; }
    public void setEventPollInterval(int eventPollInterval) { this.eventPollInterval = eventPollInterval; }

    public int getEventReconcileInterval() { return eventReconcileInterval; }
    public void setEventReconcileInterval(int eventReconcileInterval) { this.eventReconcileInterval = eventReconcileInterval; }
}
