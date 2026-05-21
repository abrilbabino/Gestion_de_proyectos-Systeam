CREATE TABLE IF NOT EXISTS blockchain_eventos (
    id                  BIGSERIAL PRIMARY KEY,
    tx_hash             VARCHAR(66) NOT NULL,
    block_number        BIGINT NOT NULL,
    tipo_evento         VARCHAR(50) NOT NULL,
    datos               TEXT,
    procesado           BOOLEAN NOT NULL DEFAULT FALSE,
    investment_id       BIGINT REFERENCES investments(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

-- Índice para buscar eventos por tx_hash (búsqueda rápida)
CREATE INDEX IF NOT EXISTS idx_eventos_tx_hash ON blockchain_eventos(tx_hash);

-- Índice para filtrar eventos no procesados
CREATE INDEX IF NOT EXISTS idx_eventos_no_procesados
    ON blockchain_eventos(procesado, created_at)
    WHERE procesado = FALSE;

CREATE TABLE IF NOT EXISTS blockchain_sync (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(50) NOT NULL UNIQUE,
    ultimo_bloque       BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Insertar el registro inicial para PaymentGateway si no existe
INSERT INTO blockchain_sync (nombre, ultimo_bloque)
SELECT 'PaymentGateway', 0
WHERE NOT EXISTS (SELECT 1 FROM blockchain_sync WHERE nombre = 'PaymentGateway');
