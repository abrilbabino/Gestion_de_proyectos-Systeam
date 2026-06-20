package com.systeam.governance.service;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.notificaciones.event.GovernanceProposalEvent;

@Service
public class GovernanceService {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final ProposalRowMapper rowMapper = new ProposalRowMapper();

    public GovernanceService(JdbcTemplate jdbc, ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProposalResponse createProposalOffChain(CreateProposalRequest request, Long userId,
                                                     String walletAddress, String txHash) {
        LocalDateTime endTime = LocalDateTime.now().plusDays(7);
        jdbc.update("""
            INSERT INTO proposals (proposer_address, proposer_user_id, title, description,
                                   proposal_type, data_bytes, status, start_time, end_time, tx_hash)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), ?, ?)
            """,
            walletAddress, userId, request.getTitle(), request.getDescription(),
            proposalTypeName(request.getProposalType()),
            request.getData() != null ? new String(request.getData()) : null,
            endTime, txHash
        );

        Long id = jdbc.queryForObject("SELECT LASTVAL()", Long.class);

        eventPublisher.publishEvent(new GovernanceProposalEvent(id, userId, GovernanceProposalEvent.Action.CREATED));

        return getProposalById(id);
    }

    @Transactional
    public void updateOnChainId(Long offChainId, BigInteger onChainId) {
        jdbc.update("UPDATE proposals SET on_chain_id = ? WHERE id = ?",
            onChainId.longValueExact(), offChainId);
    }

    public ProposalResponse getProposalById(Long id) {
        List<ProposalResponse> rows = jdbc.query(
            "SELECT p.*, u.username AS proposer_name " +
            "FROM proposals p LEFT JOIN users u ON p.proposer_user_id = u.id WHERE p.id = ?",
            rowMapper, id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ProposalResponse> listProposals(String status, int page, int size) {
        String sql = "SELECT p.*, u.username AS proposer_name " +
                     "FROM proposals p LEFT JOIN users u ON p.proposer_user_id = u.id";
        List<Object> params = new java.util.ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql += " WHERE p.status = ?";
            params.add(status.toUpperCase());
        }
        sql += " ORDER BY p.created_at DESC LIMIT ? OFFSET ?";
        params.add(size);
        params.add(page * size);

        return jdbc.query(sql, params.toArray(), rowMapper);
    }

    public long countProposals(String status) {
        String sql = "SELECT COUNT(*) FROM proposals";
        List<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql += " WHERE status = ?";
            params.add(status.toUpperCase());
        }
        Long result = jdbc.queryForObject(sql, params.toArray(), Long.class);
        return result != null ? result : 0L;
    }

    @Transactional
    public void updateVotes(Long proposalId, BigInteger forVotes, BigInteger againstVotes) {
        BigInteger total = forVotes.add(againstVotes);
        jdbc.update(
            "UPDATE proposals SET for_votes = ?, against_votes = ?, total_votes = ?, updated_at = NOW() WHERE id = ?",
            forVotes, againstVotes, total, proposalId
        );
    }

    @Transactional
    public void markExecuted(Long proposalId, LocalDateTime executedAt) {
        jdbc.update(
            "UPDATE proposals SET status = 'EXECUTED', executed_at = ?, updated_at = NOW() WHERE id = ?",
            executedAt, proposalId
        );

        eventPublisher.publishEvent(new GovernanceProposalEvent(
            proposalId, null, GovernanceProposalEvent.Action.EXECUTED
        ));
    }

    private String proposalTypeName(Integer type) {
        if (type == null) return "ProjectApproval";
        return switch (type) {
            case 0 -> "ProjectApproval";
            case 1 -> "ParameterChange";
            case 2 -> "TreasuryAction";
            default -> "ProjectApproval";
        };
    }

    private class ProposalRowMapper implements RowMapper<ProposalResponse> {
        @Override
        public ProposalResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProposalResponse r = new ProposalResponse();
            r.setId(rs.getLong("id"));
            Object onChainObj = rs.getObject("on_chain_id");
            if (onChainObj != null) {
                r.setOnChainId(BigInteger.valueOf(((Number) onChainObj).longValue()));
            }
            r.setProposerAddress(rs.getString("proposer_address"));
            r.setProposerUserId((Long) rs.getObject("proposer_user_id"));
            r.setProposerName(rs.getString("proposer_name"));
            r.setTitle(rs.getString("title"));
            r.setDescription(rs.getString("description"));
            r.setProposalType(rs.getString("proposal_type"));
            r.setStatus(rs.getString("status"));
            r.setForVotes(toBigInt(rs.getBigDecimal("for_votes")));
            r.setAgainstVotes(toBigInt(rs.getBigDecimal("against_votes")));
            r.setTotalVotes(toBigInt(rs.getBigDecimal("total_votes")));
            r.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
            r.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
            java.sql.Timestamp execTs = rs.getTimestamp("executed_at");
            r.setExecutedAt(execTs != null ? execTs.toLocalDateTime() : null);
            r.setTxHash(rs.getString("tx_hash"));
            r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return r;
        }

        private BigInteger toBigInt(java.math.BigDecimal v) {
            return v != null ? v.toBigInteger() : BigInteger.ZERO;
        }
    }
}
