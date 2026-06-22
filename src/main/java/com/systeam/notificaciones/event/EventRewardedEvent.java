package com.systeam.notificaciones.event;

import java.math.BigDecimal;

/**
 * Published after attendance is confirmed at an event and the reward
 * is credited to the attendee's wallet. Consumed by notification listeners.
 */
public class EventRewardedEvent {

    private final Long userId;
    private final Long eventoId;
    private final BigDecimal reward;

    public EventRewardedEvent(Long userId, Long eventoId, BigDecimal reward) {
        this.userId = userId;
        this.eventoId = eventoId;
        this.reward = reward;
    }

    public Long getUserId()    { return userId; }
    public Long getEventoId()  { return eventoId; }
    public BigDecimal getReward() { return reward; }
}
