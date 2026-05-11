package com.systeam.GestionDeProyectos.wallet.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public WalletSummaryResponse summary(Principal principal) {
        Long userId = getCurrentUserId(principal);
        return walletService.getSummary(userId);
    }

    private Long getCurrentUserId(Principal principal) {
        // TODO: extraer del JWT cuando se integre el filtro de autenticacion
        return 32L;
    }
}
