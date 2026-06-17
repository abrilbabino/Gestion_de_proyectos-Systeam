ALTER TABLE users ADD COLUMN IF NOT EXISTS wallet_address VARCHAR(42);
CREATE INDEX IF NOT EXISTS idx_users_wallet_address ON users(wallet_address);
