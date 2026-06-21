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
            Map<String, Object> data = jdbc.queryForMap(
                "SELECT nivel_inversor, puntos_experiencia FROM users WHERE id = ?",
                currentUserId
            );
            return data;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            Map<String, Object> defaultData = new HashMap<>();
            defaultData.put("nivel_inversor", "STARTER");
            defaultData.put("puntos_experiencia", 0);
            return defaultData;
        }
    }
}
