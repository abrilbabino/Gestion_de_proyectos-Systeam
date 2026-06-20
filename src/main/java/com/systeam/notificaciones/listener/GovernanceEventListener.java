package com.systeam.notificaciones.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.event.GovernanceProposalEvent;
import com.systeam.notificaciones.service.NotificationService;

/**
 * Listens for {@link GovernanceProposalEvent} after the governance transaction commits.
 * Creates an in-app notification — no email dispatch for governance events.
 */
@Component
public class GovernanceEventListener {

    private static final Logger log = LoggerFactory.getLogger(GovernanceEventListener.class);

    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    public GovernanceEventListener(NotificationService notificationService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGovernanceProposal(GovernanceProposalEvent event) {
        try {
            String actionLabel;
            switch (event.getAction()) {
                case CREATED:
                    actionLabel = "creada";
                    break;
                case EXECUTED:
                    actionLabel = "ejecutada";
                    break;
                case CANCELLED:
                    actionLabel = "cancelada";
                    break;
                default:
                    actionLabel = "actualizada";
            }

            String proposalTitle = jdbc.queryForObject(
                "SELECT COALESCE(title, 'Propuesta #' || id) FROM proposals WHERE id = ?",
                String.class, event.getProposalId()
            );

            String payload = String.format(
                "{\"proposalId\":%d,\"action\":\"%s\"}",
                event.getProposalId(), event.getAction().name()
            );
            notificationService.createNotification(
                event.getProposerId(),
                "GOVERNANCE_PROPOSAL",
                "Propuesta de gobernanza",
                "Propuesta \"" + proposalTitle + "\" " + actionLabel + ".",
                payload
            );
            log.info("GovernanceProposal processed for proposalId={}, action={}",
                event.getProposalId(), event.getAction());
        } catch (Exception e) {
            log.error("Failed to process GovernanceProposalEvent for proposalId={}: {}",
                event.getProposalId(), e.getMessage(), e);
        }
    }
}
