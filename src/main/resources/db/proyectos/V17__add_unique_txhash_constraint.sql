-- Elimina duplicados de tx_hash conservando el registro más antiguo (menor id)
DELETE FROM investments a USING investments b
WHERE a.id > b.id AND a.tx_hash = b.tx_hash;

-- Garantiza unicidad a nivel de base de datos
ALTER TABLE investments ADD CONSTRAINT investments_tx_hash_unique UNIQUE (tx_hash);
