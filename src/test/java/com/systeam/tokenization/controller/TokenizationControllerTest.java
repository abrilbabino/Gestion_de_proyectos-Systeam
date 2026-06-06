package com.systeam.tokenization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.security.JwtPrincipal;
import com.systeam.tokenization.dto.CreateTokenRequest;
import com.systeam.tokenization.dto.SubtokenPriceResponse;
import com.systeam.tokenization.service.SubtokenService;
import com.systeam.tokenization.service.TokenizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TokenizationController.class)
@Import(TokenizationControllerTest.TestSecurityConfig.class)
class TokenizationControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TokenizationService tokenizationService;
    @MockBean private SubtokenService subtokenService;

    @BeforeEach
    void setup() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    new JwtPrincipal(1L, "test@test.com"),
                    auth.getCredentials(),
                    auth.getAuthorities()
                )
            );
        }
    }

    @Test
    @WithMockUser
    void createToken_sinPermiso_retorna403() throws Exception {
        CreateTokenRequest request = new CreateTokenRequest();
        request.setProyectoId(1L);
        request.setCupoMaximoTokens(1000);
        request.setValorNominal(new BigDecimal("10.00"));

        mockMvc.perform(post("/api/tokens")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "project:create")
    void createToken_conPermiso_retorna201() throws Exception {
        CreateTokenRequest request = new CreateTokenRequest();
        request.setProyectoId(1L);
        request.setCupoMaximoTokens(1000);
        request.setValorNominal(new BigDecimal("10.00"));

        mockMvc.perform(post("/api/tokens")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void getTokenByProject_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/tokens/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "project:read")
    void listTokens_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/tokens"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getCurrentPrice_conAuth_retorna200() throws Exception {
        mockMvc.perform(get("/api/tokens/1/precio"))
            .andExpect(status().isOk());
    }
}
