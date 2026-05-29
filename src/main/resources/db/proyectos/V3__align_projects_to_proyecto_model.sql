DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'title') THEN
        ALTER TABLE projects RENAME COLUMN title TO titulo;
    END IF;
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'description') THEN
        ALTER TABLE projects RENAME COLUMN description TO descripcion;
    END IF;
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'required_amount') THEN
        ALTER TABLE projects RENAME COLUMN required_amount TO monto_requerido;
    END IF;
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'creator_id') THEN
        ALTER TABLE projects RENAME COLUMN creator_id TO creador_id;
    END IF;
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'status') THEN
        ALTER TABLE projects RENAME COLUMN status TO estado;
    END IF;
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'financing_end_date') THEN
        ALTER TABLE projects RENAME COLUMN financing_end_date TO plazo;
    END IF;
END $$;

ALTER TABLE projects DROP COLUMN IF EXISTS current_amount;
ALTER TABLE projects DROP COLUMN IF EXISTS cantidad_de_tokens;
ALTER TABLE projects DROP COLUMN IF EXISTS valor_nominal;
ALTER TABLE projects DROP COLUMN IF EXISTS smart_contract_address;
ALTER TABLE projects DROP COLUMN IF EXISTS financing_start_date;

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_status;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_estado') THEN
        ALTER TABLE projects ADD CONSTRAINT chk_estado
            CHECK (estado IN ('PREPARACION', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'CANCELADO'));
    END IF;
END $$;

DROP INDEX IF EXISTS idx_projects_status;
CREATE INDEX IF NOT EXISTS idx_projects_estado ON projects(estado);