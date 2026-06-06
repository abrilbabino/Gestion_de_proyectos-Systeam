package com.systeam.project.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.project.audit.dto.AuditFindingRequest;
import com.systeam.project.audit.dto.ResultadoAuditoria;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import(AuditControllerTest.TestSecurityConfig.class)
class AuditControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuditService auditService;

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
    void submitFinding_sinPermiso_retorna403() throws Exception {
        AuditFindingRequest request = new AuditFindingRequest();
        request.setKybUrl("https://kyb.example.com");
        request.setResultado(ResultadoAuditoria.APROBADO);

        mockMvc.perform(post("/api/projects/1/audit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "project:audit")
    void submitFinding_conPermiso_retorna201() throws Exception {
        AuditFindingRequest request = new AuditFindingRequest();
        request.setKybUrl("https://kyb.example.com");
        request.setResultado(ResultadoAuditoria.APROBADO);

        mockMvc.perform(post("/api/projects/1/audit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void listFindings_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/projects/1/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "project:audit")
    void listFindings_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/projects/1/audit"))
            .andExpect(status().isOk());
    }
}
