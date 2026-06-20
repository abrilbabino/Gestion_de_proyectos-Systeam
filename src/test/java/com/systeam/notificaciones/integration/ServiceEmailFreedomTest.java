package com.systeam.notificaciones.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.systeam.beneficios.service.DividendService;
import com.systeam.governance.service.GovernanceService;
import com.systeam.investment.service.InvestmentService;
import com.systeam.marketplace.service.MarketplaceService;
import com.systeam.notificaciones.service.EmailService;
import com.systeam.project.audit.AuditService;
import com.systeam.project.service.ProjectService;
import com.systeam.wallet.service.WalletService;

/**
 * Integration (structural) test: verify that refactored services no longer
 * reference {@link EmailService} and have properly adopted
 * {@link org.springframework.context.ApplicationEventPublisher}.
 *
 * This replaces the runtime guarantee that was formerly provided by direct
 * EmailService injection — now the compiler enforces it, and this test
 * double-checks the structural change.
 */
class ServiceEmailFreedomTest {

    private static final List<Class<?>> REFACTORED_SERVICES = List.of(
        WalletService.class,
        DividendService.class,
        InvestmentService.class,
        ProjectService.class,
        AuditService.class,
        GovernanceService.class,
        MarketplaceService.class
    );

    @Test
    void noRefactoredServiceDeclaresEmailServiceField() {
        for (Class<?> clazz : REFACTORED_SERVICES) {
            Field[] fields = clazz.getDeclaredFields();
            boolean hasEmailService = Arrays.stream(fields)
                .anyMatch(f -> f.getType() == EmailService.class);

            assertThat(hasEmailService)
                .as("%s must NOT declare an EmailService field", clazz.getSimpleName())
                .isFalse();
        }
    }

    @Test
    void refactoredServicesDeclareEventPublisher() {
        for (Class<?> clazz : REFACTORED_SERVICES) {
            Field[] fields = clazz.getDeclaredFields();
            boolean hasPublisher = Arrays.stream(fields)
                .anyMatch(f -> f.getType() == org.springframework.context.ApplicationEventPublisher.class);

            assertThat(hasPublisher)
                .as("%s must declare an ApplicationEventPublisher field", clazz.getSimpleName())
                .isTrue();
        }
    }

    @Test
    void allServicesAreClassesNotInterfaces() {
        for (Class<?> clazz : REFACTORED_SERVICES) {
            assertThat(clazz.isInterface())
                .as("%s must be a concrete class", clazz.getSimpleName())
                .isFalse();
        }
    }

    @Test
    void dividendServiceConstructorHasExactlyFourParameters() {
        // DividendService: JdbcTemplate, DividendDistributorService,
        //                  BlockchainService, ApplicationEventPublisher
        Stream.of(DividendService.class.getConstructors())
            .forEach(c -> assertThat(c.getParameterCount())
                .as("DividendService constructor param count")
                .isEqualTo(4));
    }

    @Test
    void walletServiceConstructorHasExactlyThreeParameters() {
        // WalletService: WalletRepository, BlockchainService, ApplicationEventPublisher
        Stream.of(WalletService.class.getConstructors())
            .forEach(c -> assertThat(c.getParameterCount())
                .as("WalletService constructor param count")
                .isEqualTo(3));
    }
}
