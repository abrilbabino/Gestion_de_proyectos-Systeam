package com.systeam.governance.controller;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceController.class);

    private final BlockchainService blockchainService;
    private final IdeaGovernanceService onChainService;
    private final GovernanceService offChainService;
    private final VoteStreamRegistry voteStreamRegistry;
    private final VoteEconomicsConfigService voteEconomicsConfigService;

    public GovernanceController(BlockchainService blockchainService,
                                 IdeaGovernanceService onChainService,
                                 GovernanceService offChainService,
                                 VoteStreamRegistry voteStreamRegistry,
                                 VoteEconomicsConfigService voteEconomicsConfigService) {
        this.blockchainService = blockchainService;
        this.onChainService = onChainService;
        this.offChainService = offChainService;
        this.voteStreamRegistry = voteStreamRegistry;
        this.voteEconomicsConfigService = voteEconomicsConfigService;
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
            if (!blockchainService.verifyTransaction(txHash)) {
                throw new RuntimeException("La transaccion de propuesta no se confirmo en blockchain");
            }
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
    public String vote(@RequestBody @Valid VoteRequest request,
                       @AuthenticationPrincipal JwtPrincipal user) {
        offChainService.validateVoteCost(user.userId());

        try {
            String txHash = onChainService.vote(
                BigInteger.valueOf(request.getProposalId()),
                request.getSupport()
            );
            offChainService.recordVoteEconomics(user.userId(), request.getProposalId(), txHash);
            return txHash;
        } catch (Exception e) {
            throw new RuntimeException("Error al votar: " + e.getMessage(), e);
        }
    }

    /**
     * SSE endpoint for real-time vote count updates on a proposal.
     * Public (no auth required) — read-only stream of vote tallies.
     */
    @GetMapping(value = "/proposals/{id}/votes/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter voteStream(@PathVariable Long id) {
        log.info("SSE subscription for proposalId={}", id);
        return voteStreamRegistry.subscribe(id);
    }

    /**
     * Returns the current vote economics configuration (cost, reward, treasury)
     * plus investor discount info for the authenticated user.
     */
    @GetMapping("/config")
    @PreAuthorize("hasAuthority('governance:read')")
    public Map<String, Object> getConfig(@AuthenticationPrincipal JwtPrincipal user) {
        VoteEconomicsConfig config = voteEconomicsConfigService.loadConfig();
        int investmentCount = offChainService.countUserInvestments(user.userId());
        BigDecimal userVoteCost = offChainService.calculateEffectiveVoteCost(user.userId());

        Map<String, Object> response = new HashMap<>();
        response.put("voteCost", config.getVoteCost());
        response.put("voteReward", config.getVoteReward());
        response.put("treasuryUserId", config.getTreasuryUserId());
        response.put("investorDiscount", config.getInvestorDiscount());
        response.put("minVoteCost", config.getMinVoteCost());
        response.put("userVoteCost", userVoteCost);
        response.put("investmentCount", investmentCount);
        return response;
    }

    @PostMapping("/proposals/{id}/execute")
    @PreAuthorize("hasAuthority('governance:execute')")
    public String executeProposal(@PathVariable Long id) {
        try {
            String txHash = onChainService.executeProposal(BigInteger.valueOf(id));
            if (!blockchainService.verifyTransaction(txHash)) {
                throw new RuntimeException("La transaccion de ejecucion no se confirmo en blockchain");
            }
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
