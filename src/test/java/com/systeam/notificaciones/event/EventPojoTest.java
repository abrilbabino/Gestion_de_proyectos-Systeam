package com.systeam.notificaciones.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class EventPojoTest {

    // ---- 1.1 InvestmentConfirmedEvent ----

    @Test
    void investmentConfirmedEvent_createsAndReturnsFields() {
        var event = new InvestmentConfirmedEvent(42L, 7L, new BigDecimal("1500.00"), 100, "0xabc123");

        assertThat(event.getInversorId()).isEqualTo(42L);
        assertThat(event.getProyectoId()).isEqualTo(7L);
        assertThat(event.getMonto()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(event.getCantidadSubTokens()).isEqualTo(100);
        assertThat(event.getTxHash()).isEqualTo("0xabc123");
    }

    // ---- 1.2 ProjectStateChangedEvent ----

    @Test
    void projectStateChangedEvent_createsAndReturnsFields() {
        var event = new ProjectStateChangedEvent(10L, "EN_REVISION", "APROBADO", 5L);

        assertThat(event.getProyectoId()).isEqualTo(10L);
        assertThat(event.getFromState()).isEqualTo("EN_REVISION");
        assertThat(event.getToState()).isEqualTo("APROBADO");
        assertThat(event.getActorId()).isEqualTo(5L);
    }

    // ---- 1.3 ProjectAuditedEvent ----

    @Test
    void projectAuditedEvent_createsAndReturnsFields() {
        var event = new ProjectAuditedEvent(10L, 5L, ProjectAuditedEvent.Result.APPROVED, 77L);

        assertThat(event.getProyectoId()).isEqualTo(10L);
        assertThat(event.getAuditorId()).isEqualTo(5L);
        assertThat(event.getResult()).isEqualTo(ProjectAuditedEvent.Result.APPROVED);
        assertThat(event.getFindingId()).isEqualTo(77L);
    }

    // ---- 1.4 GovernanceProposalEvent ----

    @Test
    void governanceProposalEvent_createsAndReturnsFields() {
        var event = new GovernanceProposalEvent(99L, 3L, GovernanceProposalEvent.Action.EXECUTED);

        assertThat(event.getProposalId()).isEqualTo(99L);
        assertThat(event.getProposerId()).isEqualTo(3L);
        assertThat(event.getAction()).isEqualTo(GovernanceProposalEvent.Action.EXECUTED);
    }

    // ---- 1.5 MarketplaceEvent ----

    @Test
    void marketplaceEvent_createsAndReturnsFields() {
        var cantidad = new BigInteger("50");
        var precio  = new BigInteger("2000000000000000000");
        var event = new MarketplaceEvent(10L, 2L, 8L, MarketplaceEvent.Type.SOLD,
            "Mi Proyecto", cantidad, precio, "0xabc");

        assertThat(event.getProjectId()).isEqualTo(10L);
        assertThat(event.getBuyerId()).isEqualTo(2L);
        assertThat(event.getSellerId()).isEqualTo(8L);
        assertThat(event.getType()).isEqualTo(MarketplaceEvent.Type.SOLD);
        assertThat(event.getProjectName()).isEqualTo("Mi Proyecto");
        assertThat(event.getCantidad()).isEqualByComparingTo(cantidad);
        assertThat(event.getPrecioUnitario()).isEqualByComparingTo(precio);
        assertThat(event.getTxHash()).isEqualTo("0xabc");
    }

    // ---- 1.6 DividendDistributedEvent ----

    @Test
    void dividendDistributedEvent_createsAndReturnsFields() {
        var event = new DividendDistributedEvent(10L, 3L, new BigDecimal("250.75"));

        assertThat(event.getProyectoId()).isEqualTo(10L);
        assertThat(event.getBeneficiarioId()).isEqualTo(3L);
        assertThat(event.getMonto()).isEqualByComparingTo(new BigDecimal("250.75"));
    }

    // ---- 1.7 WalletTransferEvent ----

    @Test
    void walletTransferEvent_createsAndReturnsFields() {
        var event = new WalletTransferEvent(1L, 2L, new BigDecimal("500.00"), "0xdef456");

        assertThat(event.getEmisorId()).isEqualTo(1L);
        assertThat(event.getDestinatarioId()).isEqualTo(2L);
        assertThat(event.getCantidad()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(event.getTxHash()).isEqualTo("0xdef456");
    }
}
