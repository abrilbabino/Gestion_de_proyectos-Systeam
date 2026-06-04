package com.systeam.tokenization.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.tokenization.dto.CreateTokenRequest;
import com.systeam.tokenization.dto.SubtokenPriceResponse;
import com.systeam.tokenization.dto.TokenResponse;
import com.systeam.tokenization.service.SubtokenService;
import com.systeam.tokenization.service.TokenizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tokens")
public class TokenizationController {

    private final TokenizationService tokenizationService;
    private final SubtokenService subtokenService;

    public TokenizationController(TokenizationService tokenizationService,
                                   SubtokenService subtokenService) {
        this.tokenizationService = tokenizationService;
        this.subtokenService = subtokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:create')")
    public TokenResponse createToken(@RequestBody @Valid CreateTokenRequest request) {
        return tokenizationService.crearToken(request);
    }

    @GetMapping("/{proyectoId}")
    @PreAuthorize("hasAuthority('project:read')")
    public TokenResponse getTokenByProject(@PathVariable Long proyectoId) {
        return tokenizationService.obtenerTokenPorProyecto(proyectoId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public Page<TokenResponse> listTokens(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return tokenizationService.listarTokens(PageRequest.of(page, size));
    }

    @GetMapping("/{proyectoId}/precio")
    public SubtokenPriceResponse getCurrentPrice(@PathVariable Long proyectoId) {
        return subtokenService.obtenerPrecioConDetalle(proyectoId);
    }
}
