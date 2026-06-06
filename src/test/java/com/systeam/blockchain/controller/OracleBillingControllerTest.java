package com.systeam.blockchain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.blockchain.dto.OracleBillingReportRequest;
import com.systeam.blockchain.service.OracleBillingService;
import com.systeam.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

@WebMvcTest(OracleBillingController.class)
@Import(OracleBillingControllerTest.TestSecurityConfig.class)
class OracleBillingControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OracleBillingService oracleBillingService;
    @MockBean private JdbcTemplate jdbc;

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
    void submitReport_sinPermiso_retorna403() throws Exception {
        OracleBillingReportRequest request = new OracleBillingReportRequest();
        request.setMontoFacturado(new BigDecimal("1000"));

        mockMvc.perform(post("/api/oracle/report/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "investment:create")
    void submitReport_conPermiso_retorna201() throws Exception {
        OracleBillingReportRequest request = new OracleBillingReportRequest();
        request.setMontoFacturado(new BigDecimal("1000"));

        mockMvc.perform(post("/api/oracle/report/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void getReport_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/oracle/report/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "investment:read")
    void getReport_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/oracle/report/1"))
            .andExpect(status().isOk());
    }
}
