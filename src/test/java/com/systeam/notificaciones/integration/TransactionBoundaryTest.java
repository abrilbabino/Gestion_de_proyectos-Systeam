package com.systeam.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Structural integration test: verify all event listener methods use
 * {@link TransactionalEventListener} with phase {@code AFTER_COMMIT}.
 *
 * This guarantees that notifications are only persisted after the business
 * transaction commits — if the service method rolls back, the event is
 * never delivered.
 */
class TransactionBoundaryTest {

    private static final List<Class<?>> LISTENER_CLASSES = List.of(
        InvestmentEventListener.class,
        ProjectEventListener.class,
        GovernanceEventListener.class,
        MarketplaceEventListener.class,
        DividendEventListener.class,
        WalletEventListener.class,
        ProjectRejectedEventListener.class
    );

    @Test
    void allListenerMethodsAreAnnotatedWithAfterCommit() {
        for (Class<?> clazz : LISTENER_CLASSES) {
            Method[] methods = clazz.getDeclaredMethods();

            boolean hasTransactionalEventListener = false;
            for (Method method : methods) {
                TransactionalEventListener ann = method.getAnnotation(TransactionalEventListener.class);
                if (ann != null) {
                    hasTransactionalEventListener = true;
                    assertThat(ann.phase().name())
                        .as("%s.%s must use AFTER_COMMIT phase", clazz.getSimpleName(), method.getName())
                        .isEqualTo("AFTER_COMMIT");
                }
            }

            assertThat(hasTransactionalEventListener)
                .as("%s must have at least one method with @TransactionalEventListener", clazz.getSimpleName())
                .isTrue();
        }
    }

    @Test
    void allListenerMethodsHaveReturnTypeVoid() {
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                TransactionalEventListener ann = method.getAnnotation(TransactionalEventListener.class);
                if (ann != null) {
                    assertThat(method.getReturnType())
                        .as("%s.%s must return void", clazz.getSimpleName(), method.getName())
                        .isEqualTo(void.class);
                }
            }
        }
    }

    @Test
    void allListenerMethodsTakeSingleEventParameter() {
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                TransactionalEventListener ann = method.getAnnotation(TransactionalEventListener.class);
                if (ann != null) {
                    assertThat(method.getParameterCount())
                        .as("%s.%s must accept exactly 1 parameter", clazz.getSimpleName(), method.getName())
                        .isEqualTo(1);
                }
            }
        }
    }
}
