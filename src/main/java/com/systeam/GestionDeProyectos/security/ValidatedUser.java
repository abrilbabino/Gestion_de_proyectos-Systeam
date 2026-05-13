package com.systeam.GestionDeProyectos.security;

import java.util.Set;

public record ValidatedUser(
        Long userId,
        String email,
        Set<String> roles,
        Set<String> permissions
) {}
