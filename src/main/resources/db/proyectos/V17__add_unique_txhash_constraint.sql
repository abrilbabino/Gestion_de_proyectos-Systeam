-- Elimina duplicados de tx_hash conservando el registro más antiguo (menor id)
DELETE FROM investments a USING investments b
WHERE a.id > b.id AND a.tx_hash = b.tx_hash;

-- Garantiza unicidad a nivel de base de datos
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'investments_tx_hash_unique') THEN
        ALTER TABLE investments ADD CONSTRAINT investments_tx_hash_unique UNIQUE (tx_hash);
    END IF;
END $$;
