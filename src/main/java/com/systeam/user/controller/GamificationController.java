package com.systeam.user.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.security.JwtPrincipal;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard/gamification")
public class GamificationController {

    private final JdbcTemplate jdbc;

    public GamificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getGamificationData(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtPrincipal principal) {
        Long currentUserId = principal.userId();
        
        try {
            Map<String, Object> data = new HashMap<>(jdbc.queryForMap(
                "SELECT nivel_inversor, puntos_experiencia FROM users WHERE id = ?",
                currentUserId
            ));

            // Contar desde portfolio_activos (misma fuente que la billetera/NFTs)
            Integer proyectosFondeados = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT s.proyecto_id) " +
                "FROM portfolio_activos pa " +
                "JOIN subtokens s ON pa.subtoken_id = s.id " +
                "WHERE pa.usuario_id = ?",
                Integer.class, currentUserId
            );
            Integer creadoresDistintos = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT p.creador_id) " +
                "FROM portfolio_activos pa " +
                "JOIN subtokens s ON pa.subtoken_id = s.id " +
                "JOIN projects p ON s.proyecto_id = p.id " +
                "WHERE pa.usuario_id = ?",
                Integer.class, currentUserId
            );

            data.put("proyectos_fondeados", proyectosFondeados != null ? proyectosFondeados : 0);
            data.put("creadores_distintos", creadoresDistintos != null ? creadoresDistintos : 0);

            return data;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            Map<String, Object> defaultData = new HashMap<>();
            defaultData.put("nivel_inversor", "STARTER");
            defaultData.put("puntos_experiencia", 0);
            defaultData.put("proyectos_fondeados", 0);
            defaultData.put("creadores_distintos", 0);
            return defaultData;
        }
    }
}
