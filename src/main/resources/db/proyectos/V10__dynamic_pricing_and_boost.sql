ALTER TABLE subtokens
    ADD COLUMN IF NOT EXISTS precio_base       DECIMAL(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS factor_volatilidad DECIMAL(5,2)  NOT NULL DEFAULT 0.50;

-- Sincronizar precio_base con el precio_actual existente
UPDATE subtokens SET precio_base = precio_actual WHERE precio_base = 0;

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS es_destacado   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fecha_boost    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS monto_boost    DECIMAL(15,2) NOT NULL DEFAULT 0;

-- tabla para distribuir ganancias a inversores según rendimiento del proyecto
CREATE TABLE IF NOT EXISTS dividendos (
    id                  BIGSERIAL PRIMARY KEY,
    proyecto_id         BIGINT NOT NULL REFERENCES projects(id),
    monto_total         DECIMAL(15,2) NOT NULL,
    monto_por_subtoken  DECIMAL(15,2) NOT NULL,
    fecha_reparto       TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dividendos_proyecto ON dividendos(proyecto_id);

-- 4. RECLAMOS: tabla para que inversores reclamen sus dividendos (o se acrediten automáticamente)
CREATE TABLE IF NOT EXISTS reclamos_dividendos (
    id                  BIGSERIAL PRIMARY KEY,
    dividendo_id        BIGINT NOT NULL REFERENCES dividendos(id),
    usuario_id          BIGINT NOT NULL REFERENCES users(id),
    subtoken_id         BIGINT NOT NULL REFERENCES subtokens(id),
    cantidad_subtokens  INTEGER NOT NULL,
    monto_recibido      DECIMAL(15,2) NOT NULL,
    reclamado_en        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reclamos_usuario ON reclamos_dividendos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_reclamos_dividendo ON reclamos_dividendos(dividendo_id);
