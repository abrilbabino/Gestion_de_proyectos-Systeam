package com.systeam.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockchainPropertiesTest {

    @Nested
    @DisplayName("getters/setters")
    class GettersSetters {

        @Test
        void setteaYobtieneRpcUrl() {
            BlockchainProperties props = new BlockchainProperties();
            props.setRpcUrl("https://sepolia.infura.io/v3/test");

            assertThat(props.getRpcUrl()).isEqualTo("https://sepolia.infura.io/v3/test");
        }

        @Test
        void setteaYobtieneContractAddresses() {
            BlockchainProperties props = new BlockchainProperties();
            props.setPaymentGatewayAddress("0x1111");
            props.setProjectTokenAddress("0x2222");
            props.setUsdcAddress("0x3333");
            props.setTreasuryAddress("0x4444");
            props.setPrivateKey("0xkey");
            props.setTokenFactoryAddress("0x5555");
            props.setInvestmentSwapAddress("0x6666");
            props.setIdeaTokenAddress("0x7777");
            props.setIdeafyFactoryAddress("0x8888");
            props.setOfferingContractAddress("0x9999");
            props.setDividendDistributorAddress("0xaaaa");
            props.setIdeaSwapAddress("0xbbbb");
            props.setIdeaMarketplaceAddress("0xcccc");
            props.setIdeaGovernanceAddress("0xdddd");
            props.setOracleBillingAddress("0xeeee");

            assertThat(props.getPaymentGatewayAddress()).isEqualTo("0x1111");
            assertThat(props.getProjectTokenAddress()).isEqualTo("0x2222");
            assertThat(props.getUsdcAddress()).isEqualTo("0x3333");
            assertThat(props.getTreasuryAddress()).isEqualTo("0x4444");
            assertThat(props.getPrivateKey()).isEqualTo("0xkey");
            assertThat(props.getTokenFactoryAddress()).isEqualTo("0x5555");
            assertThat(props.getInvestmentSwapAddress()).isEqualTo("0x6666");
            assertThat(props.getIdeaTokenAddress()).isEqualTo("0x7777");
            assertThat(props.getIdeafyFactoryAddress()).isEqualTo("0x8888");
            assertThat(props.getOfferingContractAddress()).isEqualTo("0x9999");
            assertThat(props.getDividendDistributorAddress()).isEqualTo("0xaaaa");
            assertThat(props.getIdeaSwapAddress()).isEqualTo("0xbbbb");
            assertThat(props.getIdeaMarketplaceAddress()).isEqualTo("0xcccc");
            assertThat(props.getIdeaGovernanceAddress()).isEqualTo("0xdddd");
            assertThat(props.getOracleBillingAddress()).isEqualTo("0xeeee");
        }

        @Test
        void setteaYobtieneIntervalos() {
            BlockchainProperties props = new BlockchainProperties();
            props.setEventPollInterval(5000);
            props.setEventReconcileInterval(30000);

            assertThat(props.getEventPollInterval()).isEqualTo(5000);
            assertThat(props.getEventReconcileInterval()).isEqualTo(30000);
        }
    }
}
