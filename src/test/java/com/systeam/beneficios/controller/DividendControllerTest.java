package com.systeam.beneficios.controller;

import com.systeam.beneficios.service.DividendService;
import com.systeam.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DividendController.class)
@Import(DividendControllerTest.TestSecurityConfig.class)
class DividendControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @MockBean private DividendService dividendService;

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
    void crearReparto_sinRolAdmin_retorna403() throws Exception {
        mockMvc.perform(post("/api/dividendos/proyecto/1")
                .with(csrf())
                .param("monto", "1000"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crearReparto_conRolAdmin_retorna201() throws Exception {
        mockMvc.perform(post("/api/dividendos/proyecto/1")
                .with(csrf())
                .param("monto", "1000"))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void listarRepartos_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/dividendos/proyecto/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "investment:read")
    void listarRepartos_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/dividendos/proyecto/1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void reclamarDividendos_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(post("/api/dividendos/proyecto/1/reclamar")
                .with(csrf())
                .param("wallet", "0x123"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void dividendosPendientes_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/dividendos/proyecto/1/pendientes")
                .param("wallet", "0x123"))
            .andExpect(status().isForbidden());
    }
}
