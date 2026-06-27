package com.systeam.user.controller;

import com.systeam.security.JwtPrincipal;
import com.systeam.user.service.KycService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /**
     * POST /api/kyc/create-session
     * Creates a Didit KYC verification session and returns the redirect URL.
     */
    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(@AuthenticationPrincipal JwtPrincipal principal) {
        try {
            String url = kycService.createVerificationSession(principal.userId());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/kyc/webhook
     * Receives Didit webhook events and updates the user's KYC status.
     * This endpoint must be public (no JWT required) since Didit calls it directly.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleDiditWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "X-Signature-V2", required = false) String signature,
            @RequestHeader Map<String, String> allHeaders) {

        System.out.println("WEBHOOK RECEIVED!");
        System.out.println("Headers: " + allHeaders);
        System.out.println("Payload: " + new String(payload));
        System.out.println("Signature V2: " + signature);

        // Validate signature if webhook secret is configured
        if (signature != null && !kycService.isValidSignature(payload, signature)) {
            System.out.println("Signature validation FAILED!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            kycService.processWebhook(payload);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}
