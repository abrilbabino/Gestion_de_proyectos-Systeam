ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS cupo_maximo_tokens  BIGINT,
    ADD COLUMN IF NOT EXISTS valor_nominal_token NUMERIC(15, 2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints
        WHERE table_name = 'projects' AND constraint_name = 'chk_cupo_maximo_tokens_positive'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT chk_cupo_maximo_tokens_positive
                CHECK (cupo_maximo_tokens IS NULL OR cupo_maximo_tokens > 0);
    END IF;
    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints
        WHERE table_name = 'projects' AND constraint_name = 'chk_valor_nominal_token_positive'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT chk_valor_nominal_token_positive
                CHECK (valor_nominal_token IS NULL OR valor_nominal_token > 0);
    END IF;
END $$;