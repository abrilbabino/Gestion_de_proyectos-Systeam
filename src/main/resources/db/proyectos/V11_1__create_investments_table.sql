CREATE TABLE IF NOT EXISTS investments (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT NOT NULL,
    proyecto_id          BIGINT NOT NULL,
    monto_idea           DECIMAL(15,2) NOT NULL,
    sub_tokens_recibidos INTEGER NOT NULL DEFAULT 0,
    tx_hash              VARCHAR(255),
    estado               VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE subtokens ADD COLUMN IF NOT EXISTS proyecto_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_investments_usuario_id ON investments(usuario_id);
CREATE INDEX IF NOT EXISTS idx_investments_proyecto_id ON investments(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_investments_estado ON investments(estado);
CREATE INDEX IF NOT EXISTS idx_investments_tx_hash ON investments(tx_hash);
CREATE INDEX IF NOT EXISTS idx_subtokens_proyecto_id ON subtokens(proyecto_id);

ALTER TABLE investments ADD CONSTRAINT chk_investment_estado
    CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'REEMBOLSADA'));

CREATE UNIQUE INDEX IF NOT EXISTS idx_portfolio_usuario_subtoken
    ON portfolio_activos(usuario_id, subtoken_id);

ALTER TABLE subtokens ADD CONSTRAINT fk_subtokens_proyecto
    FOREIGN KEY (proyecto_id) REFERENCES projects(id);
