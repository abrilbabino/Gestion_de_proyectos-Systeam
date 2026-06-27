CREATE TABLE IF NOT EXISTS hitos (
    id BIGSERIAL PRIMARY KEY,
    proyecto_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL,
    porcentaje DECIMAL(5,2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    comprobante_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hitos_proyecto_id ON hitos(proyecto_id);
