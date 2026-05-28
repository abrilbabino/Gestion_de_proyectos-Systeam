CREATE TABLE IF NOT EXISTS order_book (
    id BIGSERIAL PRIMARY KEY,
    on_chain_id BIGINT UNIQUE,
    seller_id BIGINT NOT NULL REFERENCES users(id),
    subtoken_id BIGINT NOT NULL REFERENCES subtokens(id),
    cantidad BIGINT NOT NULL CHECK (cantidad > 0),
    cantidad_inicial BIGINT NOT NULL CHECK (cantidad_inicial > 0),
    precio_unitario DECIMAL(40,0) NOT NULL CHECK (precio_unitario > 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tx_hash VARCHAR(66),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_book_estado ON order_book(estado);
CREATE INDEX IF NOT EXISTS idx_order_book_seller ON order_book(seller_id);
CREATE INDEX IF NOT EXISTS idx_order_book_subtoken ON order_book(subtoken_id);
