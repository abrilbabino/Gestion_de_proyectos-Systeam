-- Prevent duplicate claim registration (same on-chain tx recorded twice)
ALTER TABLE reclamos_dividendos
    ADD CONSTRAINT reclamos_dividendos_tx_hash_unique UNIQUE (tx_hash);

-- Prevent a user from claiming the same dividend distribution twice per subtoken
ALTER TABLE reclamos_dividendos
    ADD CONSTRAINT reclamos_dividendos_dividendo_usuario_subtoken_unique
    UNIQUE (dividendo_id, usuario_id, subtoken_id);
