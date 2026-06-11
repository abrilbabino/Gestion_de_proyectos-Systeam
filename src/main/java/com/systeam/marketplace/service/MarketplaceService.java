package com.systeam.marketplace.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeaMarketplaceService;
import com.systeam.marketplace.dto.CreateListingRequest;
import com.systeam.marketplace.dto.ListingResponse;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.tokenization.service.SubtokenService;

@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final JdbcTemplate jdbc;
    private final IdeaMarketplaceService ideaMarketplaceService;
    private final SubtokenService subtokenService;
    private final BlockchainService blockchainService;

    public MarketplaceService(JdbcTemplate jdbc,
                              IdeaMarketplaceService ideaMarketplaceService,
                              SubtokenService subtokenService,
                              BlockchainService blockchainService) {
        this.jdbc = jdbc;
        this.ideaMarketplaceService = ideaMarketplaceService;
        this.subtokenService = subtokenService;
        this.blockchainService = blockchainService;
    }

    @Transactional
    public ListingResponse createListing(Long sellerId, CreateListingRequest request) {
        Map<String, Object> seller = findUserOrThrow(sellerId);
        Map<String, Object> subtoken = findSubtokenOrThrow(request.getSubtokenId());

        BigDecimal precioBase = jdbc.queryForObject(
            "SELECT precio_base FROM subtokens WHERE id = ?", BigDecimal.class, request.getSubtokenId()
        );
        BigDecimal precioUnitario = new BigDecimal(request.getPrecioUnitario());
        BigDecimal precioBaseWei = precioBase.multiply(BigDecimal.TEN.pow(18));
        if (precioUnitario.compareTo(precioBaseWei) < 0) {
            throw new ConflictException(
                "El precio unitario no puede ser menor al precio base (" + precioBase + " $IDEA)"
            );
        }

        BigInteger portfolioBalance = jdbc.queryForObject(
            "SELECT COALESCE(cantidad, 0) FROM portfolio_activos WHERE usuario_id = ? AND subtoken_id = ?",
            BigInteger.class, sellerId, request.getSubtokenId()
        );
        if (portfolioBalance == null || portfolioBalance.compareTo(request.getCantidad()) < 0) {
            throw new ConflictException("No tienes suficientes sub-tokens para vender");
        }

        // Blockchain first — create listing on-chain and verify before updating DB
        try {
            String subtokenAddress = (String) subtoken.get("contract_address");
            if (subtokenAddress != null && !subtokenAddress.isBlank()) {
                String txHash = ideaMarketplaceService.listTokens(subtokenAddress, request.getCantidad(), request.getPrecioUnitario());
                if (!verifyBlockchainTx(txHash)) {
                    throw new ConflictException("La transaccion blockchain de listado fallo en Sepolia");
                }
            } else {
                throw new ConflictException("El sub-token no tiene direccion de contrato en Sepolia");
            }
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException("Error al ejecutar la transaccion blockchain: " + e.getMessage());
        }

        Long listingId = jdbc.queryForObject("""
            INSERT INTO order_book (seller_id, subtoken_id, cantidad, cantidad_inicial, precio_unitario, estado, tx_hash, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
            RETURNING id
            """, Long.class, sellerId, request.getSubtokenId(), request.getCantidad(), request.getCantidad(), request.getPrecioUnitario(), null);

        jdbc.update("""
            UPDATE portfolio_activos SET cantidad = cantidad - ?, updated_at = NOW()
            WHERE usuario_id = ? AND subtoken_id = ?
            """, request.getCantidad(), sellerId, request.getSubtokenId());

        log.info("MKT-03: Listing creada id={} seller={} cantidad={}", listingId, sellerId, request.getCantidad());
        return getListingById(listingId);
    }

    @Transactional
    public ListingResponse buyFromListing(Long buyerId, Long listingId, BigInteger cantidad) {
        Map<String, Object> listing = findListingOrThrow(listingId);

        if (!"ACTIVE".equals(listing.get("estado"))) {
            throw new ConflictException("La orden ya no esta activa");
        }

        Long sellerId = ((Number) listing.get("seller_id")).longValue();
        if (sellerId.equals(buyerId)) {
            throw new ConflictException("No puedes comprar tus propios sub-tokens");
        }

        BigInteger available = (BigInteger) listing.get("cantidad");
        if (cantidad.compareTo(available) > 0) {
            throw new ConflictException("La cantidad solicitada excede la disponible");
        }

        Long subtokenId = ((Number) listing.get("subtoken_id")).longValue();
        BigInteger precioUnitario = (BigInteger) listing.get("precio_unitario");
        BigInteger totalPrice = precioUnitario.multiply(cantidad);

        Map<String, Object> buyer = findUserOrThrow(buyerId);
        BigDecimal buyerBalance = (BigDecimal) buyer.get("saldo_idea");

        BigDecimal WEI_CONVERSION = new BigDecimal("1000000000000000000");
        BigDecimal requiredIdea = new BigDecimal(totalPrice).divide(WEI_CONVERSION, 4, java.math.RoundingMode.HALF_UP);

        if (buyerBalance.compareTo(requiredIdea) < 0) {
            throw new ConflictException(String.format(
                "Saldo insuficiente. Tienes %s IDEA y necesitas %s IDEA para esta compra.",
                buyerBalance.stripTrailingZeros().toPlainString(),
                requiredIdea.stripTrailingZeros().toPlainString()
            ));
        }

        subtokenService.validateMaxOwnership(buyerId, subtokenId, cantidad.intValue());

        // Blockchain first — send and verify before updating DB
        try {
            String txHash = ideaMarketplaceService.buyTokens(BigInteger.valueOf(listingId), cantidad);
            if (!verifyBlockchainTx(txHash)) {
                throw new ConflictException("La transaccion blockchain de compra fallo en Sepolia");
            }
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException("Error al ejecutar la transaccion blockchain: " + e.getMessage());
        }

        jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?", requiredIdea, buyerId);
        jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?", requiredIdea, sellerId);

        subtokenService.addPortfolioEntry(buyerId, subtokenId, cantidad.intValue());

        BigInteger newCantidad = available.subtract(cantidad);
        if (newCantidad.compareTo(BigInteger.ZERO) == 0) {
            jdbc.update("UPDATE order_book SET cantidad = 0, estado = 'EXECUTED', updated_at = NOW() WHERE id = ?", listingId);
        } else {
            jdbc.update("UPDATE order_book SET cantidad = ?, updated_at = NOW() WHERE id = ?", newCantidad, listingId);
        }

        log.info("MKT-03: Compra ejecutada listing={} buyer={} cantidad={} total={}", listingId, buyerId, cantidad, totalPrice);
        return getListingById(listingId);
    }

    @Transactional
    public void cancelListing(Long userId, Long listingId) {
        Map<String, Object> listing = findListingOrThrow(listingId);

        if (!userId.equals(((Number) listing.get("seller_id")).longValue())) {
            throw new ConflictException("Esta orden no te pertenece");
        }
        if (!"ACTIVE".equals(listing.get("estado"))) {
            throw new ConflictException("La orden ya no esta activa");
        }

        // Blockchain first — send and verify before updating DB
        try {
            String txHash = ideaMarketplaceService.cancelListing(BigInteger.valueOf(listingId));
            if (!verifyBlockchainTx(txHash)) {
                throw new ConflictException("La transaccion blockchain de cancelacion fallo en Sepolia");
            }
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new ConflictException("Error al ejecutar la transaccion blockchain: " + e.getMessage());
        }

        BigInteger remaining = (BigInteger) listing.get("cantidad");
        Long subtokenId = ((Number) listing.get("subtoken_id")).longValue();

        jdbc.update("UPDATE order_book SET estado = 'CANCELLED', updated_at = NOW() WHERE id = ?", listingId);

        subtokenService.addPortfolioEntry(userId, subtokenId, remaining.intValue());

        log.info("MKT-03: Listing cancelada id={} seller={}", listingId, userId);
    }

    public Page<ListingResponse> listActiveListings(Pageable pageable) {
        String countSql = "SELECT COUNT(*) FROM order_book WHERE estado = 'ACTIVE' AND cantidad > 0";
        int total = jdbc.queryForObject(countSql, Integer.class);

        String sql = """
            SELECT ob.id, ob.on_chain_id, ob.seller_id, u.name AS seller_name,
                   ob.subtoken_id, p.titulo AS project_name,
                   ob.cantidad, ob.cantidad_inicial, ob.precio_unitario,
                   (ob.cantidad * ob.precio_unitario) AS precio_total,
                   ob.estado, ob.tx_hash, ob.created_at
            FROM order_book ob
            JOIN users u ON u.id = ob.seller_id
            JOIN subtokens s ON s.id = ob.subtoken_id
            JOIN projects p ON p.id = s.proyecto_id
            WHERE ob.estado = 'ACTIVE' AND ob.cantidad > 0
            ORDER BY ob.created_at DESC
            LIMIT ? OFFSET ?
            """;

        // IMPORTANTE: Se invirtió el orden a getPageSize() (para el LIMIT) y luego getOffset()
        List<ListingResponse> listings = jdbc.query(sql, new ListingRowMapper(),
            pageable.getPageSize(), pageable.getOffset());

        return new PageImpl<>(listings, pageable, total);
    }

    public ListingResponse getListingById(Long id) {
        String sql = """
            SELECT ob.id, ob.on_chain_id, ob.seller_id, u.name AS seller_name,
                   ob.subtoken_id, p.titulo AS project_name,
                   ob.cantidad, ob.cantidad_inicial, ob.precio_unitario,
                   (ob.cantidad * ob.precio_unitario) AS precio_total,
                   ob.estado, ob.tx_hash, ob.created_at
            FROM order_book ob
            JOIN users u ON u.id = ob.seller_id
            JOIN subtokens s ON s.id = ob.subtoken_id
            JOIN projects p ON p.id = s.proyecto_id
            WHERE ob.id = ?
            """;
        List<ListingResponse> results = jdbc.query(sql, new ListingRowMapper(), id);
        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Orden no encontrada con ID: " + id);
        }
        return results.get(0);
    }

    public List<ListingResponse> listListingsBySubtoken(Long subtokenId) {
        String sql = """
            SELECT ob.id, ob.on_chain_id, ob.seller_id, u.name AS seller_name,
                   ob.subtoken_id, p.titulo AS project_name,
                   ob.cantidad, ob.cantidad_inicial, ob.precio_unitario,
                   (ob.cantidad * ob.precio_unitario) AS precio_total,
                   ob.estado, ob.tx_hash, ob.created_at
            FROM order_book ob
            JOIN users u ON u.id = ob.seller_id
            JOIN subtokens s ON s.id = ob.subtoken_id
            JOIN projects p ON p.id = s.proyecto_id
            WHERE ob.subtoken_id = ? AND ob.estado = 'ACTIVE' AND ob.cantidad > 0
            ORDER BY ob.precio_unitario ASC, ob.created_at ASC
            """;
        return jdbc.query(sql, new ListingRowMapper(), subtokenId);
    }

    private boolean verifyBlockchainTx(String txHash) {
        try {
            return blockchainService.verifyTransaction(txHash);
        } catch (Exception e) {
            log.error("Error verificando tx {}: {}", txHash, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> findUserOrThrow(Long userId) {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT id, name AS nombre, saldo_idea FROM users WHERE id = ?",
            (rs, rn) -> Map.of("id", rs.getLong("id"), "nombre", rs.getString("nombre"),
                "saldo_idea", rs.getBigDecimal("saldo_idea")),
            userId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Usuario no encontrado");
        return rows.get(0);
    }

    private Map<String, Object> findSubtokenOrThrow(Long subtokenId) {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT id, proyecto_id, contract_address FROM subtokens WHERE id = ?",
            (rs, rn) -> Map.of("id", rs.getLong("id"), "proyecto_id", rs.getLong("proyecto_id"),
                "contract_address", rs.getString("contract_address")),
            subtokenId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Sub-token no encontrado");
        return rows.get(0);
    }

    private Map<String, Object> findListingOrThrow(Long listingId) {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT id, seller_id, subtoken_id, cantidad, precio_unitario, estado FROM order_book WHERE id = ?",
            (rs, rn) -> Map.of(
                "id", rs.getLong("id"),
                "seller_id", rs.getLong("seller_id"),
                "subtoken_id", rs.getLong("subtoken_id"),
                "cantidad", rs.getBigDecimal("cantidad").toBigInteger(),
                "precio_unitario", rs.getBigDecimal("precio_unitario").toBigInteger(),
                "estado", rs.getString("estado")),
            listingId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Orden no encontrada");
        return rows.get(0);
    }

    private static class ListingRowMapper implements RowMapper<ListingResponse> {
        @Override
        public ListingResponse mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
            ListingResponse r = new ListingResponse();
            r.setId(rs.getLong("id"));
            long oid = rs.getLong("on_chain_id");
            if (!rs.wasNull()) r.setOnChainId(BigInteger.valueOf(oid));
            r.setSellerId(rs.getLong("seller_id"));
            r.setSellerName(rs.getString("seller_name"));
            r.setSubtokenId(rs.getLong("subtoken_id"));
            r.setProjectName(rs.getString("project_name"));
            r.setCantidad(rs.getBigDecimal("cantidad").toBigInteger());
            r.setCantidadInicial(rs.getBigDecimal("cantidad_inicial").toBigInteger());
            r.setPrecioUnitario(rs.getBigDecimal("precio_unitario").toBigInteger());
            r.setPrecioTotal(rs.getBigDecimal("precio_total").toBigInteger());
            r.setEstado(rs.getString("estado"));
            r.setTxHash(rs.getString("tx_hash"));
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
            return r;
        }
    }
}
