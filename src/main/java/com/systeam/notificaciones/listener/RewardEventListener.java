package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.EventRewardedEvent;
import com.systeam.notificaciones.event.VoteRewardedEvent;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for reward events (vote, event attendance) after the
 * originating transaction commits. Creates in-app notifications for each.
 */
@Component
public class RewardEventListener {

    private static final Logger log = LoggerFactory.getLogger(RewardEventListener.class);

    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    public RewardEventListener(NotificationService notificationService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteRewarded(VoteRewardedEvent event) {
        try {
            String proposalTitle = jdbc.queryForObject(
                "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
                String.class, event.getProposalId()
            );

            String payload = String.format(
                "{\"proposalId\":%d,\"reward\":%s}",
                event.getProposalId(), event.getReward().toPlainString()
            );
            notificationService.createNotification(
                event.getUserId(),
                "VOTE_REWARD",
                "Recompensa de voto",
                "Recibiste " + event.getReward().toPlainString() + " $IDEA por votar en \"" + proposalTitle + "\".",
                payload
            );
            log.info("VoteRewarded notification created for userId={}, proposalId={}",
                event.getUserId(), event.getProposalId());
        } catch (Exception e) {
            log.error("Failed to process VoteRewardedEvent for userId={}: {}",
                event.getUserId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventRewarded(EventRewardedEvent event) {
        try {
            String eventoTitle = jdbc.queryForObject(
                "SELECT COALESCE(titulo, 'Evento #' || id) FROM eventos WHERE id = ?",
                String.class, event.getEventoId()
            );

            String payload = String.format(
                "{\"eventoId\":%d,\"reward\":%s}",
                event.getEventoId(), event.getReward().toPlainString()
            );
            notificationService.createNotification(
                event.getUserId(),
                "EVENT_REWARD",
                "Recompensa de evento",
                "Recibiste " + event.getReward().toPlainString() + " $IDEA por asistir a \"" + eventoTitle + "\".",
                payload
            );
            log.info("EventRewarded notification created for userId={}, eventoId={}",
                event.getUserId(), event.getEventoId());
        } catch (Exception e) {
            log.error("Failed to process EventRewardedEvent for userId={}: {}",
                event.getUserId(), e.getMessage(), e);
        }
    }

}
