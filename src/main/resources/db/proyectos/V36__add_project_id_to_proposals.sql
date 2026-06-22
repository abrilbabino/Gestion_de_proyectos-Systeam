ALTER TABLE proposals ADD COLUMN project_id BIGINT REFERENCES projects(id);

CREATE INDEX IF NOT EXISTS idx_proposals_project_id ON proposals(project_id);
