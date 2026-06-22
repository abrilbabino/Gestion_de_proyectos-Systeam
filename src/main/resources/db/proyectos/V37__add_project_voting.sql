ALTER TABLE projects ADD COLUMN for_votes DECIMAL(40,0) NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN against_votes DECIMAL(40,0) NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN total_votes DECIMAL(40,0) NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN on_chain_proposal_id BIGINT;

CREATE TABLE IF NOT EXISTS project_votes (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    support BOOLEAN NOT NULL,
    tx_hash VARCHAR(66) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_project_votes_project ON project_votes(project_id);
CREATE INDEX IF NOT EXISTS idx_project_votes_user ON project_votes(user_id);
