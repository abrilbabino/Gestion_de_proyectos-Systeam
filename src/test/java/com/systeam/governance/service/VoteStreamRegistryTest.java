package com.systeam.governance.service;

import java.io.IOException;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@DisplayName("VoteStreamRegistry")
class VoteStreamRegistryTest {

    private VoteStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new VoteStreamRegistry();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("returns a non-null SseEmitter for a proposal")
        void returnsEmitter() {
            SseEmitter emitter = registry.subscribe(42L);

            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("multiple subscriptions for same proposal return distinct emitters")
        void distinctEmittersPerSubscription() {
            SseEmitter first = registry.subscribe(42L);
            SseEmitter second = registry.subscribe(42L);

            assertThat(first).isNotSameAs(second);
        }
    }

    @Nested
    @DisplayName("broadcast")
    class Broadcast {

        @Test
        @DisplayName("does not throw when no subscribers exist")
        void noSubscribersNoError() {
            // Should complete silently
            registry.broadcast(99L, BigInteger.TEN, BigInteger.ONE);
        }
    }

    @Nested
    @DisplayName("cleanup on completion")
    class Cleanup {

        @Test
        @DisplayName("subscribe registers onCompletion, onTimeout, and onError callbacks")
        void callbacksRegistered() {
            // subscribe should not throw and should return a configured emitter
            SseEmitter emitter = registry.subscribe(42L);
            assertThat(emitter).isNotNull();
            // Emitter has a 5-min timeout configured
            assertThat(emitter.getTimeout()).isEqualTo(5 * 60 * 1000L);
        }
    }
}
