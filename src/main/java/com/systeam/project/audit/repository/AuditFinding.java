package com.systeam.project.audit.repository;

import java.time.OffsetDateTime;

/**
 * Internal domain model for an audit finding row.
 */
public class AuditFinding {

    private Long id;
    private Long proyectoId;
    private Long auditorId;
    private String kybUrl;
    private String resultado;
    private String observaciones;
    private String txHash;
    private OffsetDateTime createdAt;

    public AuditFinding() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }

    public Long getAuditorId() { return auditorId; }
    public void setAuditorId(Long auditorId) { this.auditorId = auditorId; }

    public String getKybUrl() { return kybUrl; }
    public void setKybUrl(String kybUrl) { this.kybUrl = kybUrl; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
