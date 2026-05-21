ALTER TABLE subtokens
    ADD COLUMN IF NOT EXISTS contract_address VARCHAR(42);

CREATE INDEX IF NOT EXISTS idx_subtokens_contract_address ON subtokens(contract_address);
