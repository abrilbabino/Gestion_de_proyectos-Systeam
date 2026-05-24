package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SubtokenService {

    private final JdbcTemplate jdbc;
    private final DynamicPricingService pricingService;

    public SubtokenService(JdbcTemplate jdbc, DynamicPricingService pricingService) {
        this.jdbc = jdbc;
        this.pricingService = pricingService;
    }

    public Map<String, Object> findSubtokenByProject(Long proyectoId) {
        List<Map<String, Object>> results = jdbc.query(
            "SELECT id, cupo_restante, precio_actual, precio_base, suministro_total, " +
            "factor_volatilidad, contract_address " +
            "FROM subtokens WHERE proyecto_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("cupo_restante", rs.getInt("cupo_restante"));
                m.put("precio_actual", rs.getBigDecimal("precio_actual"));
                m.put("precio_base", rs.getBigDecimal("precio_base"));
                m.put("suministro_total", rs.getInt("suministro_total"));
                m.put("factor_volatilidad", rs.getBigDecimal("factor_volatilidad"));
                m.put("contract_address", rs.getString("contract_address"));
                return m;
            },
            proyectoId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public BigDecimal calcularPrecio(BigDecimal precioBase, int suministroTotal, int cupoRestante, BigDecimal factorVolatilidad) {
        return pricingService.calcularPrecioDinamico(precioBase, suministroTotal, cupoRestante, factorVolatilidad);
    }

    public void updateQuotaAndPrice(Long subtokenId, int subTokens, BigDecimal nuevoPrecio) {
        jdbc.update("UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ? WHERE id = ?",
            subTokens, nuevoPrecio, subtokenId);
    }

    public void addPortfolioEntry(Long usuarioId, Long subtokenId, int cantidad) {
        jdbc.update("""
            INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad, created_at, updated_at)
            VALUES (?, ?, ?, NOW(), NOW())
            ON CONFLICT (usuario_id, subtoken_id)
            DO UPDATE SET cantidad = portfolio_activos.cantidad + ?, updated_at = NOW()
            """, usuarioId, subtokenId, cantidad, cantidad);
    }

    public void removePortfolioEntry(Long usuarioId, Long proyectoId, int cantidad) {
        jdbc.update("""
            UPDATE portfolio_activos pa
            SET cantidad = GREATEST(pa.cantidad - ?, 0), updated_at = NOW()
            FROM subtokens s
            WHERE pa.subtoken_id = s.id AND s.proyecto_id = ? AND pa.usuario_id = ?
            """, cantidad, proyectoId, usuarioId);
    }
}
