package com.systeam.governance.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.governance.dto.CreateProposalRequest;
import com.systeam.governance.dto.ProposalResponse;
import com.systeam.notificaciones.event.GovernanceProposalEvent;
import com.systeam.notificaciones.event.VoteRewardedEvent;
import com.systeam.rewards.service.RewardService;
import com.systeam.voteeconomics.VoteEconomicsConfig;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
import com.systeam.wallet.service.WalletService;

@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletService walletService;
    private final RewardService rewardService;
    private final VoteEconomicsConfigService configService;
    private final VoteStreamRegistry voteStreamRegistry;
    private final ProposalRowMapper rowMapper = new ProposalRowMapper();

    public GovernanceService(JdbcTemplate jdbc, ApplicationEventPublisher eventPublisher,
                             WalletService walletService, RewardService rewardService,
                             VoteEconomicsConfigService configService,
                             VoteStreamRegistry voteStreamRegistry) {
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
        this.walletService = walletService;
        this.rewardService = rewardService;
        this.configService = configService;
        this.voteStreamRegistry = voteStreamRegistry;
    }

    @Transactional
    public ProposalResponse createProposalOffChain(CreateProposalRequest request, Long userId,
                                                     String walletAddress, String txHash) {
        LocalDateTime endTime = LocalDateTime.now().plusDays(7);
        jdbc.update("""
            INSERT INTO proposals (proposer_address, proposer_user_id, title, description,
                                   proposal_type, data_bytes, project_id, status, start_time, end_time, tx_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), ?, ?)
            """,
            walletAddress, userId, request.getTitle(), request.getDescription(),
            proposalTypeName(request.getProposalType()),
            request.getData() != null ? new String(request.getData()) : null,
            request.getProjectId(),
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
            "SELECT p.*, u.name AS proposer_name " +
            "FROM proposals p LEFT JOIN users u ON p.proposer_user_id = u.id WHERE p.id = ?",
            rowMapper, id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ProposalResponse> listProposals(String status, int page, int size) {
        String sql = "SELECT p.*, u.name AS proposer_name " +
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

    public void validateVoteCost(Long userId) {
        VoteEconomicsConfig config = configService.loadConfig();
        BigDecimal cost = config.getVoteCost();
        if (cost.signum() > 0) {
            walletService.checkSufficientBalance(userId, cost);
        }
    }

    /**
     * Records the economic side-effects of a successful on-chain vote.
     * Called AFTER the on-chain vote is confirmed. Within a single transaction:
     * 1. Deducts vote cost from voter (if cost > 0)
     * 2. Credits vote cost to treasury
     * 3. Accrues vote reward to voter (idempotent)
     * 4. Publishes VoteRewardedEvent for listeners
     * 5. Broadcasts updated tallies via SSE
     */
    @Transactional
    public void recordVoteEconomics(Long userId, Long proposalId, String txHash) {
        VoteEconomicsConfig config = configService.loadConfig();
        BigDecimal cost = config.getVoteCost();
        BigDecimal reward = config.getVoteReward();

        // 1. Deduct vote cost from voter and credit to treasury
        if (cost.signum() > 0) {
            walletService.adjustBalance(userId, cost.negate());
            if (config.getTreasuryUserId() != null) {
                walletService.adjustBalance(config.getTreasuryUserId(), cost);
            }
        }

        // 2. Accrue reward (idempotent — duplicate calls are no-ops)
        if (reward.signum() > 0) {
            rewardService.accrue(userId, "VOTE_REWARD", "PROPOSAL", proposalId, txHash, reward);
        }

        // 3. Publish event for notification listeners
        eventPublisher.publishEvent(new VoteRewardedEvent(userId, proposalId, cost, reward, txHash));

        // 4. Broadcast updated tallies via SSE
        ProposalResponse proposal = getProposalById(proposalId);
        if (proposal != null) {
            voteStreamRegistry.broadcast(proposalId, proposal.getForVotes(), proposal.getAgainstVotes());
        } else {
            voteStreamRegistry.broadcast(proposalId, BigInteger.ZERO, BigInteger.ZERO);
        }

        log.info("Vote economics recorded: userId={} proposalId={} cost={} reward={} txHash={}",
                userId, proposalId, cost, reward, txHash);
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
            Object projectIdObj = rs.getObject("project_id");
            if (projectIdObj != null) {
                r.setProjectId(((Number) projectIdObj).longValue());
            }
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
