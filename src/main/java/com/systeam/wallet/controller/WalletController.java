package com.systeam.wallet.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.security.JwtPrincipal;
import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.dto.WalletSyncRequest;
import com.systeam.wallet.service.WalletService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/summary")
    public WalletSummaryResponse summary(@AuthenticationPrincipal JwtPrincipal principal) {
        return walletService.getSummary(principal.userId());
    }

    @PostMapping("/sync-idea")
    public void syncIdeaBalance(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody @Valid WalletSyncRequest request) {
        walletService.syncIdeaBalance(principal.userId(), request.getIdeaBalance());
    }
}
