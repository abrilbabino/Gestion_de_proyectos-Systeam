-- V22: Add audit_findings and notificaciones tables + update projects estado constraint
-- Part of HU-44: Auditoria y Validacion de Proyectos

-- 1. Update the estado CHECK constraint to include EN_AUDITORIA and RECHAZADO
--    V13 already added RECHAZADO; we drop and recreate with EN_AUDITORIA added.
ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_estado;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS proyectos_estado_check;

ALTER TABLE projects ADD CONSTRAINT proyectos_estado_check
    CHECK (estado IN ('PREPARACION', 'EN_AUDITORIA', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'RECHAZADO', 'CANCELADO'));

-- 2. Create audit_findings table
CREATE TABLE IF NOT EXISTS audit_findings (
    id            BIGSERIAL    PRIMARY KEY,
    proyecto_id   BIGINT       NOT NULL REFERENCES projects(id),
    auditor_id    BIGINT       NOT NULL,
    kyb_url       VARCHAR(2048) NOT NULL,
    resultado     VARCHAR(20)  NOT NULL CHECK (resultado IN ('APROBADO', 'RECHAZADO')),
    observaciones TEXT         CHECK (char_length(observaciones) <= 2000),
    tx_hash       VARCHAR(66),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_findings_proyecto ON audit_findings(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_audit_findings_created  ON audit_findings(created_at DESC);

-- 3. Create notificaciones table
CREATE TABLE IF NOT EXISTS notificaciones (
    id                BIGSERIAL    PRIMARY KEY,
    recipient_user_id BIGINT       NOT NULL,
    type              VARCHAR(50)  NOT NULL,
    payload           JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_recipient ON notificaciones(recipient_user_id);

-- 4. Seed project:audit permission
INSERT INTO permissions (name, description)
VALUES ('project:audit', 'Auditar proyectos')
ON CONFLICT (name) DO NOTHING;
