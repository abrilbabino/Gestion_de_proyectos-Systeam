CREATE TABLE IF NOT EXISTS token_transfers (
    id              BIGSERIAL PRIMARY KEY,
    emisor_id       BIGINT NOT NULL REFERENCES users(id),
    destinatario_id BIGINT NOT NULL REFERENCES users(id),
    cantidad        DECIMAL(15,2) NOT NULL,
    tx_reference    VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_transfers_emisor ON token_transfers(emisor_id);
CREATE INDEX idx_token_transfers_destinatario ON token_transfers(destinatario_id);
