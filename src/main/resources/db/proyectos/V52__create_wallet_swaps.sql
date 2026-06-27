CREATE TABLE IF NOT EXISTS wallet_swaps (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    amount_idea DECIMAL(19, 4) NOT NULL,
    amount_usdc DECIMAL(19, 4) NOT NULL,
    tx_hash VARCHAR(66) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES users(id)
);
