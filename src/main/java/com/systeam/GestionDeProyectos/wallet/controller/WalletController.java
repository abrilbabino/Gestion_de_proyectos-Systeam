package com.systeam.GestionDeProyectos.wallet.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.GestionDeProyectos.security.JwtPrincipal;
import com.systeam.GestionDeProyectos.wallet.dto.WalletSummaryResponse;
import com.systeam.GestionDeProyectos.wallet.service.WalletService;

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
