ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS cupo_maximo_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS valor_nominal_token DECIMAL(15,2);
