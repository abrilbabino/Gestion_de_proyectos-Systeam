package com.systeam.notificaciones.event;

/**
 * Published after an audit finding on a project is committed with a definitive result.
 */
public class ProjectAuditedEvent {

    public enum Result {
        APPROVED,
        REJECTED
    }

    private final Long proyectoId;
    private final Long auditorId;
    private final Result result;
    private final Long findingId;

    public ProjectAuditedEvent(Long proyectoId, Long auditorId, Result result, Long findingId) {
        this.proyectoId = proyectoId;
        this.auditorId  = auditorId;
        this.result     = result;
        this.findingId  = findingId;
    }

    public Long getProyectoId() { return proyectoId; }
    public Long getAuditorId()  { return auditorId; }
    public Result getResult()   { return result; }
    public Long getFindingId()  { return findingId; }
}
