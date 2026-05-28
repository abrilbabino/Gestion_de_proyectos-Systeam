package com.systeam.marketplace.controller;

import java.math.BigInteger;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.systeam.marketplace.dto.CreateListingRequest;
import com.systeam.marketplace.dto.ListingResponse;
import com.systeam.marketplace.service.MarketplaceService;
import com.systeam.security.JwtPrincipal;
import com.systeam.tokenization.service.SubtokenService;
import com.systeam.tokenization.dto.SubtokenPriceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final SubtokenService subtokenService;

    public MarketplaceController(MarketplaceService marketplaceService,
                                  SubtokenService subtokenService) {
        this.marketplaceService = marketplaceService;
        this.subtokenService = subtokenService;
    }

    @GetMapping("/listings")
    public Page<ListingResponse> listActiveListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return marketplaceService.listActiveListings(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at")));
    }

    @GetMapping("/listings/{id}")
    public ListingResponse getListing(@PathVariable Long id) {
        return marketplaceService.getListingById(id);
    }

    @GetMapping("/listings/by-subtoken/{subtokenId}")
    public List<ListingResponse> listListingsBySubtoken(@PathVariable Long subtokenId) {
        return marketplaceService.listListingsBySubtoken(subtokenId);
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ListingResponse createListing(@RequestBody @Valid CreateListingRequest request,
                                          @AuthenticationPrincipal JwtPrincipal user) {
        return marketplaceService.createListing(user.userId(), request);
    }

    @PostMapping("/listings/{id}/buy")
    @PreAuthorize("isAuthenticated()")
    public ListingResponse buyFromListing(@PathVariable Long id,
                                           @RequestParam BigInteger cantidad,
                                           @AuthenticationPrincipal JwtPrincipal user) {
        return marketplaceService.buyFromListing(user.userId(), id, cantidad);
    }

    @PostMapping("/listings/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public void cancelListing(@PathVariable Long id,
                              @AuthenticationPrincipal JwtPrincipal user) {
        marketplaceService.cancelListing(user.userId(), id);
    }

    @GetMapping("/quote")
    public SubtokenPriceResponse getQuote(@RequestParam Long proyectoId) {
        SubtokenPriceResponse price = subtokenService.obtenerPrecioConDetalle(proyectoId);
        if (price == null) {
            throw new RuntimeException("Proyecto no encontrado o sin sub-token asociado");
        }
        return price;
    }
}
