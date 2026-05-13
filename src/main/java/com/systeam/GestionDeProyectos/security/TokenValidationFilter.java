package com.systeam.GestionDeProyectos.security;

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
import java.util.HashSet;
import java.util.Set;

@Component
public class TokenValidationFilter extends OncePerRequestFilter {

    private final AuthServiceClient authServiceClient;

    public TokenValidationFilter(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Le delegamos la validación al módulo auth. Si el token es inválido,
        // authServiceClient.validate() devuelve Optional.empty() y no autenticamos.
        authServiceClient.validate(authHeader).ifPresent(user -> {
            Set<SimpleGrantedAuthority> authorities = new HashSet<>();
            user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));

            JwtPrincipal principal = new JwtPrincipal(user.userId(), user.email());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        filterChain.doFilter(request, response);
    }
}
