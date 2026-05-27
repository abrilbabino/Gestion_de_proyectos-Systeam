package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.systeam.tokenization.dto.SubtokenPriceResponse;

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

    public BigDecimal calcularPrecio(BigDecimal precioBase, int suministroTotal, int cupoRestante,
                                      BigDecimal factorVolatilidad, Long proyectoId) {
        BigDecimal factorRendimiento = obtenerFactorRendimiento(proyectoId);
        return pricingService.calcularPrecioDinamico(
            precioBase, suministroTotal, cupoRestante, factorVolatilidad, factorRendimiento
        );
    }

    public SubtokenPriceResponse obtenerPrecioConDetalle(Long proyectoId) {
        Map<String, Object> subtoken = findSubtokenByProject(proyectoId);
        if (subtoken == null) return null;

        BigDecimal precioBase = (BigDecimal) subtoken.get("precio_base");
        int suministroTotal = (int) subtoken.get("suministro_total");
        int cupoRestante = (int) subtoken.get("cupo_restante");
        BigDecimal factorVolatilidad = (BigDecimal) subtoken.get("factor_volatilidad");

        int vendidos = suministroTotal - cupoRestante;
        BigDecimal demandaRelativa = suministroTotal > 0
            ? BigDecimal.valueOf(vendidos).divide(BigDecimal.valueOf(suministroTotal), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal factorDemanda = demandaRelativa.multiply(factorVolatilidad);

        BigDecimal factorRendimiento = obtenerFactorRendimiento(proyectoId);
        BigDecimal precio = calcularPrecio(precioBase, suministroTotal, cupoRestante, factorVolatilidad, proyectoId);

        return new SubtokenPriceResponse(proyectoId, precio, precioBase,
            suministroTotal, cupoRestante, factorDemanda, factorRendimiento);
    }

    public BigDecimal obtenerFactorRendimiento(Long proyectoId) {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT estado, monto_requerido, COALESCE(monto_recaudado, 0) AS monto_recaudado " +
            "FROM projects WHERE id = ?",
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("estado", rs.getString("estado"));
                m.put("monto_requerido", rs.getBigDecimal("monto_requerido"));
                m.put("monto_recaudado", rs.getBigDecimal("monto_recaudado"));
                return m;
            },
            proyectoId
        );
        if (rows.isEmpty()) return BigDecimal.ZERO;

        Map<String, Object> project = rows.get(0);
        String estado = (String) project.get("estado");

        switch (estado) {
            case "PREPARACION":
                return new BigDecimal("0.00");
            case "FINANCIAMIENTO": {
                BigDecimal montoRecaudado = (BigDecimal) project.get("monto_recaudado");
                BigDecimal montoRequerido = (BigDecimal) project.get("monto_requerido");
                if (montoRequerido == null || montoRequerido.compareTo(BigDecimal.ZERO) <= 0) {
                    return new BigDecimal("0.15");
                }
                BigDecimal progreso = montoRecaudado.divide(montoRequerido, 4, RoundingMode.HALF_UP);
                // Factor base 0.15 + hasta 0.20 extra por progreso de financiamiento
                return new BigDecimal("0.15").add(
                    progreso.multiply(new BigDecimal("0.20"))
                ).min(new BigDecimal("0.35"));
            }
            case "EJECUCION":
                return new BigDecimal("0.50");
            case "FINALIZADO":
                return new BigDecimal("0.75");
            default:
                return BigDecimal.ZERO;
        }
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
