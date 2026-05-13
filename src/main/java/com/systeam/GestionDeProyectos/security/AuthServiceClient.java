package com.systeam.GestionDeProyectos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${auth.service.url}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    // Llama a GET /auth/validate en el módulo auth pasando el Bearer token.
    // Si el token es válido, auth devuelve 200 con los datos del usuario.
    // Si es inválido o expiró, auth devuelve 401 y retornamos Optional.empty().
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
        }
    }
}
