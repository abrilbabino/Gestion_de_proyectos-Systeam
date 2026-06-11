-- 1. Crear tabla para trackear los boosts y evitar que un usuario
--    envíe el mismo txHash múltiples veces (Ataque Replay).
CREATE TABLE IF NOT EXISTS project_boosts (
    id            BIGSERIAL PRIMARY KEY,
    proyecto_id   BIGINT NOT NULL REFERENCES projects(id),
    usuario_id    BIGINT NOT NULL REFERENCES users(id),
    tx_hash       VARCHAR(255) NOT NULL UNIQUE,
    monto_gastado DECIMAL(15,2) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índices para búsquedas rápidas
CREATE INDEX IF NOT EXISTS idx_project_boosts_proyecto ON project_boosts(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_project_boosts_usuario ON project_boosts(usuario_id);
