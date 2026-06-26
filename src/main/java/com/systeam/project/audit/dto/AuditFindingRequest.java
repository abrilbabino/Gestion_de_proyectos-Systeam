package com.systeam.project.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuditFindingRequest {

    @Pattern(
        regexp = "^https?://.+",
        message = "kyb_url must be a valid HTTP or HTTPS URL"
    )
    @Size(max = 2048, message = "kyb_url must not exceed 2048 characters")
    private String kybUrl;

    @NotNull(message = "resultado is required")
    private ResultadoAuditoria resultado;

    @Size(max = 2000, message = "observaciones must not exceed 2000 characters")
    private String observaciones;

    @Size(max = 2, message = "riskScore must not exceed 2 characters")
    private String riskScore;

    private Integer financialViabilityScore;

    public String getKybUrl() { return kybUrl; }
    public void setKybUrl(String kybUrl) { this.kybUrl = kybUrl; }

    public ResultadoAuditoria getResultado() { return resultado; }
    public void setResultado(ResultadoAuditoria resultado) { this.resultado = resultado; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getRiskScore() { return riskScore; }
    public void setRiskScore(String riskScore) { this.riskScore = riskScore; }

    public Integer getFinancialViabilityScore() { return financialViabilityScore; }
    public void setFinancialViabilityScore(Integer financialViabilityScore) { this.financialViabilityScore = financialViabilityScore; }
}
