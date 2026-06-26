package com.systeam.referrals.service;

import com.systeam.project.exception.ConflictException;
import com.systeam.referrals.dto.ReferralLinkResponse;
import com.systeam.referrals.dto.ReferralStatsResponse;
import com.systeam.referrals.repository.ReferralRepository;
import com.systeam.rewards.service.RewardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ReferralService {

    static final BigDecimal REFERRAL_REWARD = new BigDecimal("50");

    @Value("${app.referral.base-url:https://ideafy.lat/registro}")
    private String baseUrl;

    private final ReferralRepository referralRepository;
    private final RewardService rewardService;

    public ReferralService(ReferralRepository referralRepository, RewardService rewardService) {
        this.referralRepository = referralRepository;
        this.rewardService = rewardService;
    }

    public ReferralLinkResponse getMyLink(Long userId) {
        String code = referralRepository.getOrGenerateCode(userId);
        return new ReferralLinkResponse(code, baseUrl + "?ref=" + code);
    }

    public void redeem(String code, Long refereeId) {
        Long referrerId = referralRepository.findReferrerByCode(code)
            .orElseThrow(() -> new ConflictException("Código de referido inválido"));

        if (referrerId.equals(refereeId)) {
            throw new ConflictException("No podés usar tu propio código");
        }

        Long referralId;
        try {
            referralId = referralRepository.insertReferral(referrerId, refereeId, code);
        } catch (DataIntegrityViolationException e) {
            // referee_id UNIQUE constraint — concurrent or duplicate redeem attempt
            throw new ConflictException("Ya fuiste referido por alguien");
        }

        rewardService.accrue(referrerId, "REFERRAL_REWARD", "referral", referralId, null, REFERRAL_REWARD);
    }

    public ReferralStatsResponse getStats(Long userId) {
        int count = referralRepository.countByReferrer(userId);
        BigDecimal totalEarned = REFERRAL_REWARD.multiply(BigDecimal.valueOf(count));
        return new ReferralStatsResponse(count, totalEarned);
    }
}
