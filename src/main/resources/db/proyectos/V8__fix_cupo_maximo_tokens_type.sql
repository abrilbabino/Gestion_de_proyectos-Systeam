-- Convierte cupo_maximo_tokens de BIGINT a INTEGER para alinear con el shared model.
ALTER TABLE projects ALTER COLUMN cupo_maximo_tokens TYPE INTEGER USING cupo_maximo_tokens::INTEGER;
