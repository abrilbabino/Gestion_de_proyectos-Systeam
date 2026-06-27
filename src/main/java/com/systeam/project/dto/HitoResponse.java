package com.systeam.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HitoResponse(
        Long id,
        String titulo,
        BigDecimal porcentaje,
        LocalDateTime plazo,
        String estado,
        String comprobanteUrl
) {}
