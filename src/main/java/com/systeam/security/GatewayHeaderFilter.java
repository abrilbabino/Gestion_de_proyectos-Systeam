package com.systeam.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");

        // Si no viene del Gateway (request pública o interna), sigue sin auth
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = request.getHeader("X-User-Email");
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (rolesHeader != null) {
            Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .forEach(r -> authorities.add(
                            new SimpleGrantedAuthority("ROLE_" + r)));
        }

        if (permissionsHeader != null) {
            Arrays.stream(permissionsHeader.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .forEach(p -> authorities.add(
                            new SimpleGrantedAuthority(p)));
        }

        JwtPrincipal principal = new JwtPrincipal(
                Long.parseLong(userId), email);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
