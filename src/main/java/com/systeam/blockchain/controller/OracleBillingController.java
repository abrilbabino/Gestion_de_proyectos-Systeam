package com.systeam.blockchain.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.blockchain.dto.OracleBillingReportRequest;
import com.systeam.blockchain.service.OracleBillingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/oracle")
public class OracleBillingController {

    private final JdbcTemplate jdbc;
    private final OracleBillingService oracleBillingService;

    public OracleBillingController(JdbcTemplate jdbc, OracleBillingService oracleBillingService) {
        this.jdbc = jdbc;
        this.oracleBillingService = oracleBillingService;
    }

    @PostMapping("/report/{proyectoId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('investment:create')")
    public Map<String, Object> submitOracleReport(
            @PathVariable Long proyectoId,
            @RequestBody @Valid OracleBillingReportRequest request) {
        return oracleBillingService.submitBillingReport(proyectoId, request.getMontoFacturado());
    }

    @GetMapping("/report/{proyectoId}")
    @PreAuthorize("hasAuthority('investment:read')")
    public List<Map<String, Object>> getOracleReport(@PathVariable Long proyectoId) {
        return jdbc.query(
            "SELECT proyecto_id, monto_facturado, fecha_reporte, oracle_address, tx_hash " +
            "FROM oracle_billing WHERE proyecto_id = ? ORDER BY fecha_reporte DESC",
            (rs, rowNum) -> Map.of(
                "validado",       true,
                "proyectoId",     rs.getLong("proyecto_id"),
                "montoFacturado", rs.getBigDecimal("monto_facturado"),
                "fechaReporte",   rs.getTimestamp("fecha_reporte").toLocalDateTime().toString(),
                "oracleAddress",  rs.getString("oracle_address"),
                "txHash",         rs.getString("tx_hash")
            ),
            proyectoId
        );
    }
}
