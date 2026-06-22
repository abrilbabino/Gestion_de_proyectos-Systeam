package com.systeam.governance.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of SSE emitters per proposalId.
 * Clients subscribe to receive real-time vote tally updates.
 */
@Component
public class VoteStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(VoteStreamRegistry.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SseEmitter for a proposal and returns it.
     * The emitter is automatically removed on timeout, completion, or error.
     */
    public SseEmitter subscribe(Long proposalId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Set<SseEmitter> proposalEmitters = emitters.computeIfAbsent(proposalId,
                k -> new CopyOnWriteArraySet<>());
        proposalEmitters.add(emitter);

        Runnable removeCallback = () -> {
            proposalEmitters.remove(emitter);
            if (proposalEmitters.isEmpty()) {
                emitters.remove(proposalId);
            }
            log.debug("SSE emitter removed for proposalId={}", proposalId);
        };

        emitter.onCompletion(removeCallback);
        emitter.onTimeout(removeCallback);
        emitter.onError(t -> {
            removeCallback.run();
            log.debug("SSE emitter error for proposalId={}: {}", proposalId, t.getMessage());
        });

        return emitter;
    }

    /**
     * Broadcasts updated vote counts to all subscribers for a given proposal.
     */
    public void broadcast(Long proposalId, BigInteger forVotes, BigInteger againstVotes) {
        Set<SseEmitter> proposalEmitters = emitters.get(proposalId);
        if (proposalEmitters == null || proposalEmitters.isEmpty()) {
            return;
        }

        BigInteger total = forVotes.add(againstVotes);
        String data = String.format(
                "{\"proposalId\":%d,\"forVotes\":%s,\"againstVotes\":%s,\"totalVotes\":%s}",
                proposalId, forVotes, againstVotes, total);

        for (SseEmitter emitter : proposalEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("vote-update")
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE for proposalId={}, removing emitter: {}",
                        proposalId, e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }
}
