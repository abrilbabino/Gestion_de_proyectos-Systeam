package com.systeam.user.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.identity.VerificationSession;
import com.stripe.net.Webhook;
import com.systeam.user.service.KycService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.systeam.user.repository.UserRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;
    private final UserRepository userRepository;

    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    public KycController(KycService kycService, UserRepository userRepository) {
        this.kycService = kycService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long userId = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getId();
                    
            String url = kycService.createVerificationSession(userId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                      @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        }

        if ("identity.verification_session.verified".equals(event.getType()) ||
            "identity.verification_session.canceled".equals(event.getType()) ||
            "identity.verification_session.requires_input".equals(event.getType())) {
            
            if (stripeObject instanceof VerificationSession) {
                VerificationSession session = (VerificationSession) stripeObject;
                kycService.processWebhook(session);
            }
        }

        return ResponseEntity.ok("Success");
    }
}
