package com.systeam.wallet.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.security.JwtPrincipal;
import com.systeam.wallet.dto.WalletSummaryResponse;
import com.systeam.wallet.service.WalletService;

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
}
