package com.systeam.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.shared.model.Usuario;
import com.systeam.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${didit.api-key}")
    private String apiKey;

    @Value("${didit.workflow-id}")
    private String workflowId;

    @Value("${didit.webhook-secret}")
    private String webhookSecret;

    @Value("${didit.api-url}")
    private String apiUrl;

    @Value("${didit.return-url}")
    private String returnUrl;

    public KycService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a Didit KYC verification session for the given user.
     * Returns the verification URL to redirect the user to.
     */
    public String createVerificationSession(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("x-api-key", apiKey);

        org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("workflow_id", workflowId);
        body.add("vendor_data", userId.toString());
        body.add("callback", returnUrl);

        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Iniciando sesión en Didit. API URL: {}, Workflow ID: '{}', API Key length: {}", 
                     apiUrl, workflowId, apiKey != null ? apiKey.length() : 0);
                     
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                apiUrl + "/v3/session/",
                request,
                JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Empty response from Didit API");
            }

            String sessionId = responseBody.path("session_id").asText();
            String verificationUrl = responseBody.path("url").asText();

            if (sessionId.isBlank() || verificationUrl.isBlank()) {
                log.error("Didit API response missing fields: {}", responseBody);
                throw new RuntimeException("Invalid response from Didit API");
            }

            // Save session ID to track this verification
            Usuario user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            user.setKycProviderId(sessionId);
            user.setKycStatus("SUBMITTED");
            userRepository.save(user);

            log.info("Didit KYC session created for userId={}, sessionId={}", userId, sessionId);
            return verificationUrl;

        } catch (Exception e) {
            log.error("Error creating Didit session for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Error al iniciar verificación de identidad: " + e.getMessage());
        }
    }

    /**
     * Validates the HMAC-SHA256 webhook signature from Didit.
     */
    public boolean isValidSignature(String payload, String signatureHeader) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes());
            String computed = HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(signatureHeader);
        } catch (Exception e) {
            log.error("Error validating Didit webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Processes the incoming Didit webhook event and updates user KYC status.
     */
    public void processWebhook(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String status = event.path("status").asText();
            String vendorData = event.path("vendor_data").asText();
            String sessionId = event.path("session_id").asText();

            if (vendorData.isBlank()) {
                log.warn("Didit webhook received with empty vendor_data, sessionId={}", sessionId);
                return;
            }

            Long userId = Long.parseLong(vendorData);
            Usuario user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

            switch (status) {
                case "Approved" -> {
                    user.setKycStatus("VERIFIED");
                    log.info("KYC VERIFIED for userId={}, sessionId={}", userId, sessionId);
                }
                case "Declined" -> {
                    user.setKycStatus("REJECTED");
                    log.info("KYC REJECTED for userId={}, sessionId={}", userId, sessionId);
                }
                case "In Review" -> {
                    user.setKycStatus("IN_REVIEW");
                    log.info("KYC IN_REVIEW for userId={}, sessionId={}", userId, sessionId);
                }
                default -> log.info("Didit webhook status ignored: {} for userId={}", status, userId);
            }

            userRepository.save(user);

        } catch (Exception e) {
            log.error("Error processing Didit webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Error procesando webhook de Didit");
        }
    }
}
