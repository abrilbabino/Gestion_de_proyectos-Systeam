package com.systeam.wallet.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.systeam.wallet.dto.TransferTokensResponse;
import com.systeam.wallet.dto.WalletHistoryItem;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {

    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal findSaldoIdea(Long userId) {
        return jdbc.queryForObject(
            "SELECT saldo_idea FROM users WHERE id = ?",
            BigDecimal.class, userId
        );
    }

    public void updateSaldoIdea(Long userId, BigDecimal balance) {
        jdbc.update("UPDATE users SET saldo_idea = ? WHERE id = ?", balance, userId);
    }

    public BigDecimal findSaldoUsdt(Long userId) {
        return jdbc.queryForObject(
            "SELECT saldo_usdt FROM users WHERE id = ?",
            BigDecimal.class, userId
        );
    }

    public List<Object[]> findPortfolio(Long userId) {
        return jdbc.query(
            "SELECT s.id AS subtoken_id, p.titulo AS proyecto_nombre, s.nombre AS subtoken_nombre, s.simbolo AS subtoken_simbolo, pa.cantidad, s.precio_actual, s.contract_address, p.estado AS proyecto_estado " +
            "FROM portfolio_activos pa " +
            "JOIN subtokens s ON pa.subtoken_id = s.id " +
            "JOIN projects p ON s.proyecto_id = p.id " +
            "WHERE pa.usuario_id = ?",
            (rs, rowNum) -> new Object[]{
                rs.getLong("subtoken_id"),
                rs.getString("proyecto_nombre"),
                rs.getString("subtoken_nombre"),
                rs.getString("subtoken_simbolo"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_actual"),
                rs.getString("contract_address"),
                rs.getString("proyecto_estado")
            },
            userId
        );
    }
    public List<WalletHistoryItem> findHistory(Long usuarioId, LocalDateTime desde, LocalDateTime hasta) {
        String sql = """
        SELECT * FROM (
            SELECT 'COMPRA' AS tipo, monto_idea AS monto, sub_tokens_recibidos AS cantidad,
                   tx_hash, ('Inversion proyecto ' || (SELECT titulo FROM projects WHERE id = proyecto_id)) AS descripcion, created_at AS fecha
            FROM investments WHERE usuario_id = ? AND estado = 'CONFIRMADA'

            UNION ALL

            SELECT 'DIVIDENDO' AS tipo, monto_recibido AS monto, cantidad_subtokens AS cantidad,
                   NULL AS tx_hash, 'Cobro de dividendos' AS descripcion, reclamado_en AS fecha
            FROM reclamos_dividendos WHERE usuario_id = ?

            UNION ALL

            SELECT 'VENTA' AS tipo, ((precio_unitario / 1e18) * (cantidad_inicial - cantidad)) AS monto,
                   (cantidad_inicial - cantidad) AS cantidad,
                   tx_hash, 'Venta de tokens' AS descripcion, updated_at AS fecha
            FROM order_book WHERE seller_id = ? AND cantidad < cantidad_inicial

            UNION ALL

            SELECT 'TRANSFERENCIA_ENVIADA' AS tipo, cantidad AS monto, NULL AS cantidad,
                   tx_hash AS tx_hash, 'Transferencia enviada' AS descripcion, created_at AS fecha
            FROM token_transfers WHERE emisor_id = ?

            UNION ALL

            SELECT 'TRANSFERENCIA_RECIBIDA' AS tipo, cantidad AS monto, NULL AS cantidad,
                   tx_hash AS tx_hash, 'Transferencia recibida' AS descripcion, created_at AS fecha
            FROM token_transfers WHERE destinatario_id = ?
        ) historial
        WHERE 1=1
        """;

        List<Object> params = new ArrayList<>(List.of(
            usuarioId, usuarioId, usuarioId, usuarioId, usuarioId
        ));

        if (desde != null) {
            sql += " AND fecha >= ?";
            params.add(Timestamp.valueOf(desde));
        }
        if (hasta != null) {
            sql += " AND fecha <= ?";
            params.add(Timestamp.valueOf(hasta));
        }

        sql += " ORDER BY fecha DESC";

        return jdbc.query(sql,
            (rs, rowNum) -> WalletHistoryItem.builder()
                .tipo(rs.getString("tipo"))
                .monto(rs.getBigDecimal("monto"))
                .cantidad((Long) rs.getObject("cantidad"))
                .txHash(rs.getString("tx_hash"))
                .descripcion(rs.getString("descripcion"))
                .fecha(rs.getTimestamp("fecha").toLocalDateTime())
                .build(),
            params.toArray()
        );
    }

    public void updateWalletAddress(Long userId, String walletAddress) {
        jdbc.update("UPDATE users SET wallet_address = ? WHERE id = ?", walletAddress, userId);
    }

    public boolean userExists(Long userId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId
        );
        return count != null && count > 0;
    }

    public boolean txHashExists(String txHash) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM token_transfers WHERE tx_hash = ?", Integer.class, txHash
        );
        return count != null && count > 0;
    }

    public void debitAndCredit(Long fromUserId, Long toUserId, BigDecimal amount) {
        jdbc.update("UPDATE users SET saldo_idea = saldo_idea - ? WHERE id = ?", amount, fromUserId);
        jdbc.update("UPDATE users SET saldo_idea = saldo_idea + ? WHERE id = ?", amount, toUserId);
    }

    public TransferTokensResponse saveTransfer(Long fromUserId, Long toUserId, BigDecimal amount, String txHash) {
        Long id = jdbc.queryForObject(
            "INSERT INTO token_transfers (emisor_id, destinatario_id, cantidad, tx_hash) VALUES (?, ?, ?, ?) RETURNING id",
            Long.class, fromUserId, toUserId, amount, txHash
        );
        LocalDateTime now = LocalDateTime.now();
        return TransferTokensResponse.builder()
            .id(id)
            .emisorId(fromUserId)
            .destinatarioId(toUserId)
            .cantidad(amount)
            .txHash(txHash)
            .fecha(now)
            .build();
    }

    public List<TransferTokensResponse> findTransfersByUser(Long userId) {
        return jdbc.query(
            "SELECT id, emisor_id, destinatario_id, cantidad, tx_hash, created_at " +
            "FROM token_transfers WHERE emisor_id = ? OR destinatario_id = ? ORDER BY created_at DESC",
            (rs, rowNum) -> TransferTokensResponse.builder()
                .id(rs.getLong("id"))
                .emisorId(rs.getLong("emisor_id"))
                .destinatarioId(rs.getLong("destinatario_id"))
                .cantidad(rs.getBigDecimal("cantidad"))
                .txHash(rs.getString("tx_hash"))
                .fecha(rs.getTimestamp("created_at").toLocalDateTime())
                .build(),
            userId, userId
        );
    }
}
