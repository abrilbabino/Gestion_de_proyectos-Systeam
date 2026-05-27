CREATE TABLE IF NOT EXISTS proposals (
    id BIGSERIAL PRIMARY KEY,
    on_chain_id BIGINT UNIQUE,
    proposer_address VARCHAR(42) NOT NULL,
    proposer_user_id BIGINT REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    proposal_type VARCHAR(50) NOT NULL DEFAULT 'ProjectApproval',
    data_bytes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    for_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    against_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    total_votes DECIMAL(40,0) NOT NULL DEFAULT 0,
    start_time TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    tx_hash VARCHAR(66),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status);
CREATE INDEX IF NOT EXISTS idx_proposals_on_chain_id ON proposals(on_chain_id);
CREATE INDEX IF NOT EXISTS idx_proposals_proposer ON proposals(proposer_user_id);
