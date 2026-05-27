package com.systeam.governance.controller;

import java.math.BigInteger;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.blockchain.service.IdeaGovernanceService;
import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.governance.dto.VoteRequest;
import com.systeam.governance.service.GovernanceService;
import com.systeam.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final IdeaGovernanceService onChainService;
    private final GovernanceService offChainService;

    public GovernanceController(IdeaGovernanceService onChainService,
                                 GovernanceService offChainService) {
        this.onChainService = onChainService;
        this.offChainService = offChainService;
    }

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('governance:create')")
    public ProposalResponse createProposal(@RequestBody @Valid CreateProposalRequest request,
                                            @AuthenticationPrincipal JwtPrincipal user) {
        try {
            String txHash = onChainService.createProposal(
                request.getDescription(),
                request.getProposalType(),
                request.getData() != null ? request.getData() : new byte[0]
            );
            ProposalResponse saved = offChainService.createProposalOffChain(
                request, user.userId(), null, txHash
            );
            BigInteger onChainId = onChainService.getProposalCount();
            offChainService.updateOnChainId(saved.getId(), onChainId);
            return getProposal(saved.getId());
        } catch (Exception e) {
            throw new RuntimeException("Error al crear propuesta en blockchain: " + e.getMessage(), e);
        }
    }

    @PostMapping("/vote")
    @PreAuthorize("hasAuthority('governance:vote')")
    public String vote(@RequestBody @Valid VoteRequest request) {
        try {
            String txHash = onChainService.vote(
                BigInteger.valueOf(request.getProposalId()),
                request.getSupport()
            );
            return txHash;
        } catch (Exception e) {
            throw new RuntimeException("Error al votar: " + e.getMessage(), e);
        }
    }

    @PostMapping("/proposals/{id}/execute")
    @PreAuthorize("hasAuthority('governance:execute')")
    public String executeProposal(@PathVariable Long id) {
        try {
            String txHash = onChainService.executeProposal(BigInteger.valueOf(id));
            offChainService.markExecuted(id, java.time.LocalDateTime.now());
            return txHash;
        } catch (Exception e) {
            throw new RuntimeException("Error al ejecutar propuesta: " + e.getMessage(), e);
        }
    }

    @GetMapping("/proposals")
    @PreAuthorize("hasAuthority('governance:read')")
    public List<ProposalResponse> listProposals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return offChainService.listProposals(status, page, size);
    }

    @GetMapping("/proposals/{id}")
    @PreAuthorize("hasAuthority('governance:read')")
    public ProposalResponse getProposal(@PathVariable Long id) {
        ProposalResponse r = offChainService.getProposalById(id);
        if (r == null) {
            throw new RuntimeException("Propuesta no encontrada: " + id);
        }
        return r;
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('governance:read')")
    public long getCount(@RequestParam(required = false) String status) {
        try {
            return offChainService.countProposals(status);
        } catch (Exception e) {
            return 0;
        }
    }
}
