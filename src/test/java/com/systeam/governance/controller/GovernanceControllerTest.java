package com.systeam.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.blockchain.service.IdeaGovernanceService;
import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.governance.dto.VoteRequest;
import com.systeam.governance.service.GovernanceService;
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

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GovernanceController.class)
@Import(GovernanceControllerTest.TestSecurityConfig.class)
class GovernanceControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private IdeaGovernanceService onChainService;
    @MockBean private GovernanceService offChainService;

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
    void createProposal_sinPermiso_retorna403() throws Exception {
        CreateProposalRequest request = new CreateProposalRequest();
        request.setTitle("Test title");
        request.setDescription("Test proposal");
        request.setProposalType(1);

        mockMvc.perform(post("/api/governance/proposals")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "governance:create")
    void createProposal_conPermiso_retorna201() throws Exception {
        when(onChainService.createProposal(any(), any(), any())).thenReturn("0xtx");
        when(offChainService.createProposalOffChain(any(), any(), any(), any())).thenReturn(new ProposalResponse());
        when(onChainService.getProposalCount()).thenReturn(BigInteger.ONE);
        when(offChainService.getProposalById(any())).thenReturn(new ProposalResponse());

        CreateProposalRequest request = new CreateProposalRequest();
        request.setTitle("Test title");
        request.setDescription("Test proposal");
        request.setProposalType(1);

        mockMvc.perform(post("/api/governance/proposals")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void vote_sinPermiso_retorna403() throws Exception {
        VoteRequest request = new VoteRequest();
        request.setProposalId(1L);
        request.setSupport(true);

        mockMvc.perform(post("/api/governance/vote")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "governance:vote")
    void vote_conPermiso_retorna200() throws Exception {
        when(onChainService.vote(any(), any())).thenReturn("0xtx");

        VoteRequest request = new VoteRequest();
        request.setProposalId(1L);
        request.setSupport(true);

        mockMvc.perform(post("/api/governance/vote")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void executeProposal_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(post("/api/governance/proposals/1/execute")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "governance:execute")
    void executeProposal_conPermiso_retorna200() throws Exception {
        when(onChainService.executeProposal(any())).thenReturn("0xtx");

        mockMvc.perform(post("/api/governance/proposals/1/execute")
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void listProposals_sinPermiso_retorna403() throws Exception {
        mockMvc.perform(get("/api/governance/proposals"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "governance:read")
    void listProposals_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/governance/proposals"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "governance:read")
    void getProposal_conPermiso_retorna200() throws Exception {
        when(offChainService.getProposalById(1L)).thenReturn(new ProposalResponse());

        mockMvc.perform(get("/api/governance/proposals/1"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "governance:read")
    void getCount_conPermiso_retorna200() throws Exception {
        mockMvc.perform(get("/api/governance/count"))
            .andExpect(status().isOk());
    }
}
