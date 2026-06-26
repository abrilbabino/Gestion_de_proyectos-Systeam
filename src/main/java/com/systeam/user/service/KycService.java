package com.systeam.user.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.identity.VerificationSession;
import com.stripe.param.identity.VerificationSessionCreateParams;
import com.systeam.shared.model.Usuario;
import com.systeam.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KycService {

    private final UserRepository userRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    public KycService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String createVerificationSession(Long userId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        Usuario user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        VerificationSessionCreateParams params = VerificationSessionCreateParams.builder()
                .setType(VerificationSessionCreateParams.Type.DOCUMENT)
                .setReturnUrl("http://localhost:5173/perfil")
                .putMetadata("userId", userId.toString())
                .build();

        VerificationSession session = VerificationSession.create(params);

        user.setKycProviderId(session.getId());
        user.setKycStatus("SUBMITTED");
        userRepository.save(user);

        return session.getUrl();
    }

    public void processWebhook(VerificationSession session) {
        String userIdStr = session.getMetadata().get("userId");
        if (userIdStr == null) return;

        Long userId = Long.parseLong(userIdStr);
        Usuario user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if ("verified".equals(session.getStatus())) {
            user.setKycStatus("VERIFIED");
        } else if ("canceled".equals(session.getStatus()) || "requires_input".equals(session.getStatus())) {
            user.setKycStatus("REJECTED");
        }
        
        userRepository.save(user);
    }
}
