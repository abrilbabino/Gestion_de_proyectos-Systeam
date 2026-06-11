package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.systeam.project.exception.ConflictException;
import com.systeam.tokenization.dto.SubtokenPriceResponse;

@Service
public class SubtokenService {

    private final JdbcTemplate jdbc;
    private final DynamicPricingService pricingService;
    private final BigDecimal maxOwnershipPercent;

    public SubtokenService(JdbcTemplate jdbc, DynamicPricingService pricingService,
                           @Value("${app.tokens.max-ownership-percent:0.50}") BigDecimal maxOwnershipPercent) {
        this.jdbc = jdbc;
        this.pricingService = pricingService;
        this.maxOwnershipPercent = maxOwnershipPercent;
    }

    public Map<String, Object> findSubtokenByProject(Long proyectoId) {
        List<Map<String, Object>> results = jdbc.query(
            "SELECT id, simbolo, cupo_restante, precio_actual, precio_base, suministro_total, " +
            "factor_volatilidad, contract_address " +
            "FROM subtokens WHERE proyecto_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("simbolo", rs.getString("simbolo"));
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
        BigDecimal sobreOferta = obtenerSobreOferta(proyectoId);
        return pricingService.calcularPrecioDinamico(
            precioBase, suministroTotal, cupoRestante, factorVolatilidad, factorRendimiento, sobreOferta
        );
    }

    public BigDecimal obtenerSobreOferta(Long proyectoId) {
        List<Map<String, Object>> subtokens = jdbc.query(
            "SELECT id, suministro_total FROM subtokens WHERE proyecto_id = ?",
            (rs, rowNum) -> Map.of("id", rs.getLong("id"), "total", rs.getInt("suministro_total")),
            proyectoId
        );
        if (subtokens.isEmpty()) return BigDecimal.ZERO;

        Long subtokenId = (Long) subtokens.get(0).get("id");
        int total = (int) subtokens.get(0).get("total");
        if (total <= 0) return BigDecimal.ZERO;

        Integer cantidadListada = jdbc.queryForObject(
            "SELECT COALESCE(SUM(cantidad), 0) FROM order_book WHERE subtoken_id = ? AND estado = 'ACTIVE'",
            Integer.class, subtokenId
        );
        if (cantidadListada == null || cantidadListada <= 0) return BigDecimal.ZERO;

        BigDecimal ratio = BigDecimal.valueOf(cantidadListada)
            .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return ratio.min(BigDecimal.ONE);
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

        String estado = obtenerEstadoProyecto(proyectoId);

        BigDecimal precio;
        BigDecimal factorRendimiento;
        BigDecimal sobreOferta;

        if ("FINANCIAMIENTO".equals(estado)) {
            BigDecimal montoRecaudado = obtenerMontoRecaudado(proyectoId);
            BigDecimal montoRequerido = obtenerMontoRequerido(proyectoId);
            precio = pricingService.calcularPrecioFinanciamiento(
                precioBase, montoRecaudado, montoRequerido
            );
            factorRendimiento = BigDecimal.ZERO;
            sobreOferta = BigDecimal.ZERO;
        } else {
            factorRendimiento = obtenerFactorRendimiento(proyectoId);
            sobreOferta = obtenerSobreOferta(proyectoId);
            precio = calcularPrecio(precioBase, suministroTotal, cupoRestante, factorVolatilidad, proyectoId);
        }

        return new SubtokenPriceResponse(proyectoId, precio, precioBase,
            suministroTotal, cupoRestante, factorDemanda, factorRendimiento, sobreOferta);
    }

    private String obtenerEstadoProyecto(Long proyectoId) {
        try {
            return jdbc.queryForObject("SELECT estado FROM projects WHERE id = ?", String.class, proyectoId);
        } catch (Exception e) {
            return null;
        }
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
            case "FINANCIAMIENTO":
                return BigDecimal.ZERO;
            case "EJECUCION":
                return new BigDecimal("0.50");
            case "FINALIZADO":
                return new BigDecimal("0.75");
            default:
                return BigDecimal.ZERO;
        }
    }

    public void updateQuotaAndPrice(Long subtokenId, int amountDeducted, BigDecimal newPrice) {
        jdbc.update(
            "UPDATE subtokens SET cupo_restante = cupo_restante - ?, precio_actual = ?, updated_at = NOW() WHERE id = ?",
            amountDeducted, newPrice, subtokenId
        );
    }

    public void validateMaxOwnership(Long usuarioId, Long subtokenId, int cantidadAComprar) {
        Integer suministroTotal = jdbc.queryForObject(
            "SELECT suministro_total FROM subtokens WHERE id = ?", Integer.class, subtokenId
        );

        if (suministroTotal == null || suministroTotal <= 0) {
            return;
        }

        Integer cantidadActual = jdbc.queryForObject(
            "SELECT COALESCE(MAX(cantidad), 0) FROM portfolio_activos WHERE usuario_id = ? AND subtoken_id = ?",
            Integer.class, usuarioId, subtokenId
        );
        if (cantidadActual == null) cantidadActual = 0;

        int maxPermitido = BigDecimal.valueOf(suministroTotal).multiply(maxOwnershipPercent).intValue();

        if ((cantidadActual + cantidadAComprar) > maxPermitido) {
            throw new ConflictException(
                "Has excedido el limite maximo de tenencia del " +
                maxOwnershipPercent.multiply(new BigDecimal("100")).intValue() + "% (" + maxPermitido + " tokens) para este proyecto. " +
                "Actualmente tienes " + cantidadActual + " y estas intentando comprar " + cantidadAComprar + "."
            );
        }
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

    public BigDecimal obtenerMontoRecaudado(Long proyectoId) {
        try {
            BigDecimal monto = jdbc.queryForObject(
                "SELECT COALESCE(monto_recaudado, 0) FROM projects WHERE id = ?",
                BigDecimal.class, proyectoId
            );
            return monto != null ? monto : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal obtenerMontoRequerido(Long proyectoId) {
        try {
            BigDecimal monto = jdbc.queryForObject(
                "SELECT monto_requerido FROM projects WHERE id = ?",
                BigDecimal.class, proyectoId
            );
            return monto != null ? monto : BigDecimal.ONE;
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }
}
