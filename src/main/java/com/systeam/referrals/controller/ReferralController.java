package com.systeam.referrals.controller;

import com.systeam.referrals.dto.RedeemReferralRequest;
import com.systeam.referrals.dto.ReferralLinkResponse;
import com.systeam.referrals.dto.ReferralStatsResponse;
import com.systeam.referrals.service.ReferralService;
import com.systeam.security.JwtPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    private Long currentUserId() {
        return ((JwtPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId();
    }

    @GetMapping("/my-link")
    public ResponseEntity<ReferralLinkResponse> myLink() {
        return ResponseEntity.ok(referralService.getMyLink(currentUserId()));
    }

    @PostMapping("/redeem")
    public ResponseEntity<Void> redeem(@RequestBody RedeemReferralRequest request) {
        referralService.redeem(request.code(), currentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-stats")
    public ResponseEntity<ReferralStatsResponse> myStats() {
        return ResponseEntity.ok(referralService.getStats(currentUserId()));
    }
}
