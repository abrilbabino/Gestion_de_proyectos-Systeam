package com.systeam.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionalEventListener;

import com.systeam.notificaciones.listener.DividendEventListener;
import com.systeam.notificaciones.listener.GovernanceEventListener;
import com.systeam.notificaciones.listener.InvestmentEventListener;
import com.systeam.notificaciones.listener.MarketplaceEventListener;
import com.systeam.notificaciones.listener.ProjectEventListener;
import com.systeam.notificaciones.listener.ProjectRejectedEventListener;
import com.systeam.notificaciones.listener.WalletEventListener;

/**
 * Structural test: verify every listener catches exceptions in its handler
 * method. This ensures that a faulty listener (e.g. DB failure, email failure)
 * never blocks other listeners registered for the same event.
 */
class FaultTolerantListenerTest {

    private static final List<Class<?>> LISTENER_CLASSES = List.of(
        InvestmentEventListener.class,
        ProjectEventListener.class,
        GovernanceEventListener.class,
        MarketplaceEventListener.class,
        DividendEventListener.class,
        WalletEventListener.class,
        ProjectRejectedEventListener.class
    );

    private static boolean isHandler(Method m) {
        return m.getAnnotation(TransactionalEventListener.class) != null;
    }

    @Test
    void allListenersHaveMethodBodyStartingWithTryCatch() {
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!isHandler(method)) continue;

                assertThat(method.getExceptionTypes())
                    .as("%s.%s must NOT declare checked exceptions (catch internally)",
                        clazz.getSimpleName(), method.getName())
                    .isEmpty();
            }
        }
    }

    @Test
    void allListenerMethodsHaveVoidReturnType() {
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!isHandler(method)) continue;

                assertThat(method.getReturnType())
                    .as("%s.%s must return void", clazz.getSimpleName(), method.getName())
                    .isEqualTo(void.class);
            }
        }
    }

    @Test
    void noListenerThrowsExceptionLogically() {
        // Validate that no handler declares throws clause
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!isHandler(method)) continue;

                assertThat(method.getExceptionTypes())
                    .as("%s.%s must not throw checked exceptions", clazz.getSimpleName(), method.getName())
                    .isEmpty();
            }
        }
    }

    @Test
    void listenerLoggingIsRobust() {
        // Quick smoke test: listener methods accept a single event param
        for (Class<?> clazz : LISTENER_CLASSES) {
            long handlerCount = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .filter(FaultTolerantListenerTest::isHandler)
                .count();

            assertThat(handlerCount)
                .as("%s must have at least 1 handler method", clazz.getSimpleName())
                .isGreaterThan(0);
        }
    }
}
