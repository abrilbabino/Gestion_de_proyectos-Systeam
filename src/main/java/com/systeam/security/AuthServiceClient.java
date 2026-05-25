package com.systeam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestClient restClient;

    public AuthServiceClient(@Value("${auth.service.url}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    // Llama a GET /auth/validate en el módulo auth pasando el Bearer token.
    // Si el token es válido, auth devuelve 200 con los datos del usuario.
    // Si es inválido/expiró (4xx) o el servicio no responde, retornamos Optional.empty().
    public Optional<ValidatedUser> validate(String authorizationHeader) {
        try {
            ValidatedUser user = restClient.get()
                    .uri("/auth/validate")
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(ValidatedUser.class);
            return Optional.ofNullable(user);
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        } catch (HttpServerErrorException e) {
            log.warn("Auth service returned server error during validation: {}", e.getStatusCode());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("Auth service unreachable during validation: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
