package com.systeam.marketplace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.marketplace.dto.CreateListingRequest;
import com.systeam.marketplace.service.MarketplaceService;
import com.systeam.security.JwtPrincipal;
import com.systeam.tokenization.dto.SubtokenPriceResponse;
import com.systeam.tokenization.service.SubtokenService;
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

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketplaceController.class)
@Import(MarketplaceControllerTest.TestSecurityConfig.class)
class MarketplaceControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MarketplaceService marketplaceService;
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
    void listActiveListings_retorna200() throws Exception {
        mockMvc.perform(get("/api/marketplace/listings"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getListing_retorna200() throws Exception {
        mockMvc.perform(get("/api/marketplace/listings/1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getQuote_retorna200() throws Exception {
        when(subtokenService.obtenerPrecioConDetalle(anyLong()))
            .thenReturn(new SubtokenPriceResponse());
        mockMvc.perform(get("/api/marketplace/quote")
                .param("proyectoId", "1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void createListing_retorna201() throws Exception {
        CreateListingRequest request = new CreateListingRequest();
        request.setSubtokenId(1L);
        request.setCantidad(BigInteger.valueOf(10));
        request.setPrecioUnitario(BigInteger.valueOf(100));

        mockMvc.perform(post("/api/marketplace/listings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

}
