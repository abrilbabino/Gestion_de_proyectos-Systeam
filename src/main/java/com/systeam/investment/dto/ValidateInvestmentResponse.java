package com.systeam.investment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateInvestmentResponse {
    private boolean valido;
    private String mensaje;
    private Integer cupoDisponible;
    private java.math.BigDecimal precioSubtoken;
    private Integer subTokensARecebir;
    private java.math.BigDecimal descuentoPorcentaje;
}
