package com.systeam.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.systeam.security.GatewayHeaderFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayHeaderFilter gatewayHeaderFilter;

    public SecurityConfig(GatewayHeaderFilter gatewayHeaderFilter) {
        this.gatewayHeaderFilter = gatewayHeaderFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173",
            "https://sip-2026-systeam-frontend.vercel.app",
            "https://*.vercel.app",
            "https://project-1944cf83-7f15-4f33-89b.web.app",
            "https://ideafy.lat"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                // 401 cuando no hay token o el token es inválido/expirado
                .authenticationEntryPoint((req, res, e) -> res.sendError(401, "No autenticado"))
                // 403 cuando el token es válido pero no tiene el permiso
                .accessDeniedHandler((req, res, e) -> res.sendError(403, "Acceso denegado"))
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Endpoints públicos (no requieren token)
                .requestMatchers(HttpMethod.GET, "/api/projects/catalog").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/modules/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/governance/proposals/*/votes/stream").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/projects/*/votes/stream").permitAll()
                .requestMatchers("/estado.html", "/static/**", "/css/**", "/js/**").permitAll()
                // Didit KYC webhook — called directly by Didit, no JWT
                .requestMatchers("/api/kyc/webhook").permitAll()
                // Todo lo demás requiere estar autenticado (+ @PreAuthorize en el controller)
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
