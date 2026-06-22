package com.systeam.gamification.controller;

import com.systeam.gamification.dto.WrappedResponse;
import com.systeam.gamification.service.WrappedService;
import com.systeam.security.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/wrapped")
public class WrappedController {

    private final WrappedService wrappedService;

    public WrappedController(WrappedService wrappedService) {
        this.wrappedService = wrappedService;
    }

    @GetMapping
    public WrappedResponse getMyWrapped(@AuthenticationPrincipal JwtPrincipal principal) {
        return wrappedService.getWrappedMetrics(principal.userId());
    }
}
