package com.systeam.project.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ProjectVoteStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProjectVoteStreamRegistry.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long projectId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.computeIfAbsent(projectId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> set = emitters.get(projectId);
            if (set != null) {
                set.remove(emitter);
                if (set.isEmpty()) emitters.remove(projectId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void broadcast(Long projectId, long forVotes, long againstVotes) {
        Set<SseEmitter> set = emitters.get(projectId);
        if (set == null || set.isEmpty()) return;

        String json = String.format(
            "{\"projectId\":%d,\"forVotes\":%d,\"againstVotes\":%d,\"totalVotes\":%d}",
            projectId, forVotes, againstVotes, forVotes + againstVotes);

        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("vote-update").data(json));
            } catch (IOException e) {
                set.remove(emitter);
            }
        }
    }
}
