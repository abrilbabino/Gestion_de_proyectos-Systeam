package com.systeam.notificaciones.event;

/**
 * Published after a project audit finding with resultado=RECHAZADO is committed.
 * All IDs are primitives; listener re-queries if full entity state is needed.
 */
public class ProjectRejectedEvent {

    private final Long proyectoId;
    private final Long auditorId;
    private final Long findingId;
    private final Long creadorId;

    public ProjectRejectedEvent(Long proyectoId, Long auditorId, Long findingId, Long creadorId) {
        this.proyectoId = proyectoId;
        this.auditorId  = auditorId;
        this.findingId  = findingId;
        this.creadorId  = creadorId;
    }

    public Long getProyectoId() { return proyectoId; }
    public Long getAuditorId()  { return auditorId; }
    public Long getFindingId()  { return findingId; }
    public Long getCreadorId()  { return creadorId; }
}
