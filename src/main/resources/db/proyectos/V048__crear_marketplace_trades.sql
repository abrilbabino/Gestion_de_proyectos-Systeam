CREATE TABLE IF NOT EXISTS marketplace_trades (
    id SERIAL PRIMARY KEY,
    listing_id INTEGER REFERENCES order_book(id),
    buyer_id INTEGER REFERENCES users(id),
    seller_id INTEGER REFERENCES users(id),
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(38,0) NOT NULL,
    tx_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
