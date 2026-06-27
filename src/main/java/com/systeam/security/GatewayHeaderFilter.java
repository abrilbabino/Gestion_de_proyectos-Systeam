package com.systeam.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayHeaderFilter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String authHeader = request.getHeader("Authorization");

        log.info("GatewayHeaderFilter: path={}, X-User-Id={}, Authorization={}",
            request.getRequestURI(), userId, authHeader != null ? "Bearer present" : "none");

        if (userId != null) {
            log.info("Using X-User-Id header path");
            authenticateFromHeaders(userId,
                    request.getHeader("X-User-Email"),
                    request.getHeader("X-User-Roles"),
                    request.getHeader("X-User-Permissions"));
            filterChain.doFilter(request, response);
            return;
        }

        // Local dev path: no gateway, read JWT directly
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.info("Using JWT Bearer path");
            tryAuthenticateFromJwt(authHeader.substring(7));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateFromHeaders(String userId, String email,
            String rolesHeader, String permissionsHeader) {

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (rolesHeader != null) {
            Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }

        if (permissionsHeader != null) {
            Arrays.stream(permissionsHeader.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }

        JwtPrincipal principal = new JwtPrincipal(Long.parseLong(userId), email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @SuppressWarnings("unchecked")
    private void tryAuthenticateFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return;

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes,
                    new TypeReference<Map<String, Object>>() {});

            Object userIdClaim = claims.get("userId");
            if (userIdClaim == null) return;

            Long extractedUserId = ((Number) userIdClaim).longValue();
            String email = (String) claims.get("sub");

            Set<SimpleGrantedAuthority> authorities = new HashSet<>();

            List<String> permissions = (List<String>) claims.get("permissions");
            if (permissions != null) {
                permissions.stream()
                        .filter(p -> p != null && !p.isEmpty())
                        .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            }

            List<String> roles = (List<String>) claims.get("roles");
            if (roles != null) {
                roles.stream()
                        .filter(r -> r != null && !r.isEmpty())
                        .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }

            JwtPrincipal principal = new JwtPrincipal(extractedUserId, email);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));

        } catch (Exception e) {
            log.error("JWT authentication failed", e);
        }
    }
}
