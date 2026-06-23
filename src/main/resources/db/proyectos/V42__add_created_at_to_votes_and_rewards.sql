ALTER TABLE project_votes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
UPDATE project_votes SET created_at = NOW() WHERE created_at IS NULL;

ALTER TABLE reward_ledger ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
UPDATE reward_ledger SET created_at = NOW() WHERE created_at IS NULL;
