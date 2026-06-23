ALTER TABLE reclamos_dividendos ADD COLUMN IF NOT EXISTS tx_hash VARCHAR(66);
CREATE INDEX IF NOT EXISTS idx_reclamos_tx_hash ON reclamos_dividendos(tx_hash);
