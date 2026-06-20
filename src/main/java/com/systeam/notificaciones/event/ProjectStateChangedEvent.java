package com.systeam.notificaciones.event;

/**
 * Published after a project's estado field transitions to a new value.
 * States are String-typed to stay decoupled from the project module's enum.
 */
public class ProjectStateChangedEvent {

    private final Long proyectoId;
    private final String fromState;
    private final String toState;
    private final Long actorId;

    public ProjectStateChangedEvent(Long proyectoId, String fromState, String toState, Long actorId) {
        this.proyectoId = proyectoId;
        this.fromState  = fromState;
        this.toState    = toState;
        this.actorId    = actorId;
    }

    public Long getProyectoId() { return proyectoId; }
    public String getFromState() { return fromState; }
    public String getToState()   { return toState; }
    public Long getActorId()     { return actorId; }
}
