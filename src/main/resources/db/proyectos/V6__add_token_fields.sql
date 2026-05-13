-- cupo_maximo_tokens y valor_nominal_token ya existen como BIGINT/NUMERIC desde V4.
-- Se agregan con IF NOT EXISTS por compatibilidad en entornos sin V4 previo.
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS cupo_maximo_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS valor_nominal_token DECIMAL(15,2);
