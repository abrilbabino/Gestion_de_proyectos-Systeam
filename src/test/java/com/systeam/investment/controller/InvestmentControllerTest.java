package com.systeam.investment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.investment.dto.CreateInvestmentRequest;
import com.systeam.investment.dto.ValidateInvestmentRequest;
import com.systeam.investment.service.InvestmentService;
import com.systeam.security.JwtPrincipal;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestmentController.class)
@Import(InvestmentControllerTest.TestSecurityConfig.class)
class InvestmentControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InvestmentService investmentService;

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
    void validateInvestment_conAuth_retorna200() throws Exception {
        ValidateInvestmentRequest request = new ValidateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("100"));

        mockMvc.perform(post("/api/investments/validate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void createInvestment_sinPermiso_retorna403() throws Exception {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("100"));
        request.setTxHash("0xtxhash");

        mockMvc.perform(post("/api/investments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "investment:create")
    void createInvestment_conPermiso_retorna201() throws Exception {
        CreateInvestmentRequest request = new CreateInvestmentRequest();
        request.setProyectoId(1L);
        request.setMontoIdea(new BigDecimal("100"));
        request.setTxHash("0xtxhash");

        mockMvc.perform(post("/api/investments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void getInvestmentById_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/investments/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "investment:read")
    void getInvestmentById_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/investments/1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "investment:read")
    void getInvestmentHistory_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/investments/history"))
            .andExpect(status().isOk());
    }
}
