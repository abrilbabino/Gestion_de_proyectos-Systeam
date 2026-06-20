package com.systeam.notificaciones.event;

import java.math.BigDecimal;

/**
 * Published after a dividend distribution is committed to a beneficiary.
 */
public class DividendDistributedEvent {

    private final Long proyectoId;
    private final Long beneficiarioId;
    private final BigDecimal monto;

    public DividendDistributedEvent(Long proyectoId, Long beneficiarioId, BigDecimal monto) {
        this.proyectoId      = proyectoId;
        this.beneficiarioId  = beneficiarioId;
        this.monto           = monto;
    }

    public Long getProyectoId()      { return proyectoId; }
    public Long getBeneficiarioId()  { return beneficiarioId; }
    public BigDecimal getMonto()     { return monto; }
}
