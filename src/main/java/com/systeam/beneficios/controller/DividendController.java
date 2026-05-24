package com.systeam.beneficios.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.beneficios.service.DividendService;
import com.systeam.security.JwtPrincipal;

@RestController
@RequestMapping("/api/dividendos")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @PostMapping("/proyecto/{proyectoId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> crearReparto(
            @PathVariable Long proyectoId,
            @RequestParam BigDecimal monto) {
        Long id = dividendService.crearReparto(proyectoId, monto);
        return Map.of("id", id, "mensaje", "Reparto de dividendos creado exitosamente");
    }

    @GetMapping("/proyecto/{proyectoId}")
    @PreAuthorize("hasAuthority('investment:read')")
    public List<Map<String, Object>> listarRepartos(@PathVariable Long proyectoId) {
        return dividendService.listarRepartos(proyectoId);
    }

    @PostMapping("/{dividendoId}/reclamar")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('investment:read')")
    public Map<String, String> reclamarDividendos(
            @PathVariable Long dividendoId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        dividendService.reclamarDividendos(dividendoId, principal.userId());
        return Map.of("mensaje", "Dividendos reclamados exitosamente");
    }

    @GetMapping("/mis-reclamos")
    @PreAuthorize("hasAuthority('investment:read')")
    public List<Map<String, Object>> misReclamos(@AuthenticationPrincipal JwtPrincipal principal) {
        return dividendService.listarReclamosUsuario(principal.userId());
    }
}
