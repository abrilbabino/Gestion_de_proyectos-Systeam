package com.systeam.project.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectVoteRepository {

    private final JdbcTemplate jdbc;

    public ProjectVoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasVoted(Long projectId, Long userId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM project_votes WHERE project_id = ? AND user_id = ?",
            Integer.class, projectId, userId);
        return count != null && count > 0;
    }

    public void insertVote(Long projectId, Long userId, boolean support, String txHash) {
        jdbc.update(
            "INSERT INTO project_votes (project_id, user_id, support, tx_hash) VALUES (?, ?, ?, ?)",
            projectId, userId, support, txHash);
    }

    public void incrementVoteCounts(Long projectId, boolean support) {
        if (support) {
            jdbc.update(
                "UPDATE projects SET for_votes = for_votes + 1, total_votes = total_votes + 1, updated_at = NOW() WHERE id = ?",
                projectId);
        } else {
            jdbc.update(
                "UPDATE projects SET against_votes = against_votes + 1, total_votes = total_votes + 1, updated_at = NOW() WHERE id = ?",
                projectId);
        }
    }

    public long[] getVoteCounts(Long projectId) {
        try {
            return jdbc.queryForObject(
                "SELECT for_votes, against_votes, total_votes FROM projects WHERE id = ?",
                (rs, rowNum) -> new long[]{
                    rs.getBigDecimal("for_votes").longValue(),
                    rs.getBigDecimal("against_votes").longValue(),
                    rs.getBigDecimal("total_votes").longValue()
                }, projectId);
        } catch (Exception e) {
            return new long[]{0, 0, 0};
        }
    }

    public Long getOnChainProposalId(Long projectId) {
        try {
            return jdbc.queryForObject(
                "SELECT on_chain_proposal_id FROM projects WHERE id = ?",
                Long.class, projectId);
        } catch (Exception e) {
            return null;
        }
    }

    public void setOnChainProposalId(Long projectId, Long onChainProposalId) {
        jdbc.update(
            "UPDATE projects SET on_chain_proposal_id = ? WHERE id = ?",
            onChainProposalId, projectId);
    }
}
