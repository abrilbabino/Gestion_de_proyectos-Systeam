package com.systeam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.Credentials;
import org.web3j.tx.gas.DefaultGasProvider;

@Configuration
@EnableConfigurationProperties(BlockchainProperties.class)
public class Web3jConfig {

    @Bean
    public Web3j web3j(BlockchainProperties props) {
        return Web3j.build(new HttpService(props.getRpcUrl()));
    }

    @Bean
    public Credentials credentials(BlockchainProperties props) {
        return Credentials.create(props.getPrivateKey());
    }
}
