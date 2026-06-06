package com.systeam.project.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuditFindingRequest {

    @NotBlank(message = "kyb_url is required")
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

    public String getKybUrl() { return kybUrl; }
    public void setKybUrl(String kybUrl) { this.kybUrl = kybUrl; }

    public ResultadoAuditoria getResultado() { return resultado; }
    public void setResultado(ResultadoAuditoria resultado) { this.resultado = resultado; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
