package com.systeam.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.crypto.Credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Web3jConfigTest {

    @Mock
    private BlockchainProperties props;

    private final Web3jConfig config = new Web3jConfig();

    @Nested
    @DisplayName("web3j bean")
    class Web3jBean {

        @Test
        void buildWithRpcUrl() {
            when(props.getRpcUrl()).thenReturn("http://localhost:8545");

            Web3j web3j = config.web3j(props);

            assertThat(web3j).isNotNull();
        }
    }

    @Nested
    @DisplayName("credentials bean")
    class CredentialsBean {

        @Test
        void createFromPrivateKey() {
            when(props.getPrivateKey()).thenReturn(
                "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

            Credentials credentials = config.credentials(props);

            assertThat(credentials).isNotNull();
            assertThat(credentials.getAddress()).isNotBlank();
        }
    }
}
