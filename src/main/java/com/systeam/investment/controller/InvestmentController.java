package com.systeam.investment.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.investment.dto.CreateInvestmentRequest;
import com.systeam.investment.dto.InvestmentResponse;
import com.systeam.investment.dto.ValidateInvestmentRequest;
import com.systeam.investment.dto.ValidateInvestmentResponse;
import com.systeam.investment.service.InvestmentService;
import com.systeam.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @PostMapping("/validate")
    public ValidateInvestmentResponse validateInvestment(
            @RequestBody @Valid ValidateInvestmentRequest request) {
        return investmentService.validateInvestment(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('investment:create')")
    public InvestmentResponse createInvestment(
            @RequestBody @Valid CreateInvestmentRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return investmentService.createInvestment(request, principal.userId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('investment:read')")
    public InvestmentResponse getInvestmentById(@PathVariable Long id) {
        return investmentService.getInvestmentById(id);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('investment:read')")
    public Page<InvestmentResponse> getInvestmentHistory(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return investmentService.getInvestmentHistory(principal.userId(), PageRequest.of(page, size));
    }
}
