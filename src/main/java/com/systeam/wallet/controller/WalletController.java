package com.systeam.wallet.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.security.JwtPrincipal;
import com.systeam.wallet.dto.WalletHistoryItem;
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

    @GetMapping("/history")
    public List<WalletHistoryItem> history(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return walletService.getHistory(principal.userId(), desde, hasta);
    }
}
