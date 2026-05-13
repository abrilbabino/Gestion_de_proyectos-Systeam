package com.systeam.GestionDeProyectos.security;

public record JwtPrincipal(Long userId, String email) {}
