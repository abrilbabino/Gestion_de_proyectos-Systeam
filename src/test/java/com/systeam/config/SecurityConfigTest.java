package com.systeam.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.systeam.security.GatewayHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private SecurityConfig config;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        GatewayHeaderFilter filter = new GatewayHeaderFilter();
        config = new SecurityConfig(filter);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/projects");
    }

    @Nested
    @DisplayName("corsConfigurationSource")
    class CorsConfigurationSourceTest {

        @Test
        void permiteOrigenesEspecificos() {
            CorsConfigurationSource source = config.corsConfigurationSource();

            assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
            CorsConfiguration corsConfig = source.getCorsConfiguration(request);
            assertThat(corsConfig.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder(
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "https://sip-2026-systeam-frontend.vercel.app",
                    "https://*.vercel.app"
                );
        }

        @Test
        void permiteMetodosHttpEspecificos() {
            CorsConfigurationSource source = config.corsConfigurationSource();
            CorsConfiguration corsConfig = source.getCorsConfiguration(request);

            assertThat(corsConfig.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
        }

        @Test
        void permiteTodosLosHeaders_yCredentials() {
            CorsConfigurationSource source = config.corsConfigurationSource();
            CorsConfiguration corsConfig = source.getCorsConfiguration(request);

            assertThat(corsConfig.getAllowedHeaders()).containsExactly("*");
            assertThat(corsConfig.getAllowCredentials()).isTrue();
            assertThat(corsConfig.getMaxAge()).isEqualTo(3600L);
        }
    }
}
