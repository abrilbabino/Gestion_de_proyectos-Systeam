package com.systeam.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeaGovernanceService;
import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.governance.dto.VoteRequest;
import com.systeam.governance.service.GovernanceService;
import com.systeam.governance.service.VoteStreamRegistry;
import com.systeam.security.JwtPrincipal;
import com.systeam.voteeconomics.VoteEconomicsConfig;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
    @MockBean private BlockchainService blockchainService;
    @MockBean private IdeaGovernanceService onChainService;
    @MockBean private GovernanceService offChainService;
    @MockBean private VoteStreamRegistry voteStreamRegistry;
    @MockBean private VoteEconomicsConfigService voteEconomicsConfigService;

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
            when(blockchainService.verifyTransaction("0xtx")).thenReturn(true);
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
    void vote_conPermiso_retorna200YRecordaEconomics() throws Exception {
        when(onChainService.vote(any(), any())).thenReturn("0xtx");

        VoteRequest request = new VoteRequest();
        request.setProposalId(1L);
        request.setSupport(true);

        mockMvc.perform(post("/api/governance/vote")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(offChainService).validateVoteCost(anyLong());
        verify(offChainService).recordVoteEconomics(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(authorities = "governance:vote")
    void vote_saldoInsuficiente_noEnviaTransaccionOnChain() throws Exception {
        org.mockito.Mockito.doThrow(new com.systeam.project.exception.ConflictException("Saldo insuficiente para realizar la operación"))
            .when(offChainService).validateVoteCost(anyLong());

        VoteRequest request = new VoteRequest();
        request.setProposalId(1L);
        request.setSupport(true);

        mockMvc.perform(post("/api/governance/vote")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());

        verify(onChainService, org.mockito.Mockito.never()).vote(any(), any());
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
            when(blockchainService.verifyTransaction("0xtx")).thenReturn(true);

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

    @Test
    @WithMockUser(authorities = "governance:read")
    void getConfig_retorna200() throws Exception {
        when(voteEconomicsConfigService.loadConfig())
            .thenReturn(new VoteEconomicsConfig(java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(5), null));

        mockMvc.perform(get("/api/governance/config"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void voteStream_retorna200ConEventStream() throws Exception {
        when(voteStreamRegistry.subscribe(42L))
            .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(30000L));

        mockMvc.perform(get("/api/governance/proposals/42/votes/stream")
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk());

        verify(voteStreamRegistry).subscribe(42L);
    }
}
