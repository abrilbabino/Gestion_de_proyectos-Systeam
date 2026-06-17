ALTER TABLE token_transfers RENAME COLUMN tx_reference TO tx_hash;
ALTER TABLE token_transfers ALTER COLUMN tx_hash TYPE VARCHAR(66);

DROP INDEX IF EXISTS idx_token_transfers_emisor;
DROP INDEX IF EXISTS idx_token_transfers_destinatario;

CREATE INDEX idx_token_transfers_emisor ON token_transfers(emisor_id);
CREATE INDEX idx_token_transfers_destinatario ON token_transfers(destinatario_id);
CREATE INDEX idx_token_transfers_tx_hash ON token_transfers(tx_hash);
