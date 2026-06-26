package com.systeam.gamification.service;

import com.systeam.gamification.dto.WrappedResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class WrappedService {

    private final JdbcTemplate jdbcTemplate;

    public WrappedService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WrappedResponse getWrappedMetrics(Long userId) {
        WrappedResponse response = new WrappedResponse();

        // 1. Total Invertido
        String sqlTotalInvertido = "SELECT COALESCE(SUM(monto_idea), 0) FROM investments WHERE usuario_id = ?";
        BigDecimal totalInvertido = jdbcTemplate.queryForObject(sqlTotalInvertido, BigDecimal.class, userId);
        response.setTotalInvertido(totalInvertido);

        // 2. Proyectos Fondeados distintos
        String sqlProyectosFondeados = "SELECT COUNT(DISTINCT proyecto_id) FROM investments WHERE usuario_id = ?";
        Integer proyectosFondeados = jdbcTemplate.queryForObject(sqlProyectosFondeados, Integer.class, userId);
        response.setProyectosFondeados(proyectosFondeados != null ? proyectosFondeados : 0);

        // 3. Rubro Favorito
        String sqlRubroFavorito = "SELECT CAST(p.rubro AS VARCHAR) as cant " +
                "FROM investments i " +
                "JOIN projects p ON i.proyecto_id = p.id " +
                "WHERE i.usuario_id = ? " +
                "GROUP BY p.rubro " +
                "ORDER BY COUNT(i.id) DESC " +
                "LIMIT 1";
        List<String> rubros = jdbcTemplate.queryForList(sqlRubroFavorito, String.class, userId);
        String rubroName = "Ninguno";
        if (!rubros.isEmpty() && rubros.get(0) != null) {
            String rubroId = rubros.get(0);
            switch (rubroId) {
                case "1": rubroName = "Tech"; break;
                case "2": rubroName = "Gastronómico"; break;
                case "3": rubroName = "Inmobiliario"; break;
                case "4": rubroName = "Agro"; break;
                default: rubroName = "Innovación"; break;
            }
            
            // Top 3 proyectos del rubro favorito
            String sqlTopProyectosRubro = "SELECT p.titulo, SUM(i.monto_idea) as total FROM investments i " +
                    "JOIN projects p ON i.proyecto_id = p.id " +
                    "WHERE i.usuario_id = ? AND p.rubro = ? " +
                    "GROUP BY p.titulo " +
                    "ORDER BY total DESC " +
                    "LIMIT 3";
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(sqlTopProyectosRubro, userId, Integer.parseInt(rubroId));
            List<String> topProyectos = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> row : rows) {
                topProyectos.add((String) row.get("titulo"));
            }
            response.setTopProyectosRubro(topProyectos);
        }
        response.setRubroFavorito(rubroName);

        // Proyecto más invertido
        String sqlProyectoMasInvertido = "SELECT p.titulo, SUM(i.monto_idea) as total " +
                "FROM investments i " +
                "JOIN projects p ON i.proyecto_id = p.id " +
                "WHERE i.usuario_id = ? " +
                "GROUP BY p.titulo " +
                "ORDER BY total DESC " +
                "LIMIT 1";
        try {
            java.util.Map<String, Object> masInvertido = jdbcTemplate.queryForMap(sqlProyectoMasInvertido, userId);
            response.setProyectoMasInvertido((String) masInvertido.get("titulo"));
            response.setMontoMasInvertido(new BigDecimal(masInvertido.get("total").toString()));
        } catch (Exception e) {
            // Ignorar si no hay inversiones
        }

        // 4. ROI (Retorno / Invertido)
        String sqlTotalDividendos = "SELECT COALESCE(SUM(monto_recibido), 0) FROM reclamos_dividendos WHERE usuario_id = ?";
        BigDecimal totalDividendos = BigDecimal.ZERO;
        try {
            totalDividendos = jdbcTemplate.queryForObject(sqlTotalDividendos, BigDecimal.class, userId);
        } catch (Exception e) {
            // Tabla dividendos might not exist or be different
        }
        
        if (totalDividendos == null) totalDividendos = BigDecimal.ZERO;

        BigDecimal roiPorcentaje = BigDecimal.ZERO;
        if (totalInvertido != null && totalInvertido.compareTo(BigDecimal.ZERO) > 0) {
            roiPorcentaje = totalDividendos.divide(totalInvertido, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        response.setRoiPorcentaje(roiPorcentaje);

        // 5. Nivel del Usuario
        String sqlNivel = "SELECT nivel_inversor FROM users WHERE id = ?";
        String nivel = null;
        try {
            nivel = jdbcTemplate.queryForObject(sqlNivel, String.class, userId);
        } catch (Exception e) {
            // ignore
        }
        response.setNivel(nivel != null ? nivel : "STARTER");

        // 6. Ranking (Si es PARTNER o VISIONARY, calculamos su posición global según monto invertido)
        if ("PARTNER".equalsIgnoreCase(response.getNivel()) || "VISIONARY".equalsIgnoreCase(response.getNivel())) {
            // Calcula cuantos usuarios invirtieron MAS que este usuario
            String sqlRanking = "SELECT COUNT(*) FROM (" +
                    "  SELECT usuario_id, SUM(monto_idea) as total " +
                    "  FROM investments " +
                    "  GROUP BY usuario_id" +
                    ") as ranking WHERE total > ?";
            Integer usersAbove = jdbcTemplate.queryForObject(sqlRanking, Integer.class, totalInvertido);
            response.setRankingPosicion((usersAbove != null ? usersAbove : 0) + 1);

            String sqlTotalUsers = "SELECT COUNT(DISTINCT usuario_id) FROM investments";
            Integer totalUsers = jdbcTemplate.queryForObject(sqlTotalUsers, Integer.class);
            response.setRankingTotalUsuarios(totalUsers);
        }

        // 7. Marketplace Metrics
        try {
            String sqlMarketplaceTx = "SELECT COUNT(*) FROM order_book WHERE seller_id = ?";
            Integer txMarketplace = jdbcTemplate.queryForObject(sqlMarketplaceTx, Integer.class, userId);
            response.setTransaccionesMarketplace(txMarketplace != null ? txMarketplace : 0);

            String sqlMarketplaceVol = "SELECT COALESCE(SUM(precio_unitario * cantidad_inicial), 0) FROM order_book WHERE seller_id = ?";
            BigDecimal volMarketplaceWei = jdbcTemplate.queryForObject(sqlMarketplaceVol, BigDecimal.class, userId);
            BigDecimal weiConversion = new BigDecimal("1000000000000000000");
            BigDecimal volMarketplaceIdea = (volMarketplaceWei != null) ? 
                volMarketplaceWei.divide(weiConversion, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
            response.setVolumenMarketplace(volMarketplaceIdea);
        } catch (Exception e) {
            response.setTransaccionesMarketplace(0);
            response.setVolumenMarketplace(BigDecimal.ZERO);
        }

        return response;
    }
}
