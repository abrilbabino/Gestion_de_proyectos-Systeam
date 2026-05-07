-- Renombrar columnas para alinear con el modelo compartido Proyecto
ALTER TABLE projects RENAME COLUMN title TO titulo;
ALTER TABLE projects RENAME COLUMN description TO descripcion;
ALTER TABLE projects RENAME COLUMN required_amount TO monto_requerido;
ALTER TABLE projects RENAME COLUMN creator_id TO creador_id;
ALTER TABLE projects RENAME COLUMN status TO estado;
ALTER TABLE projects RENAME COLUMN financing_end_date TO plazo;

-- Eliminar columnas que no existen en Proyecto
ALTER TABLE projects DROP COLUMN IF EXISTS current_amount;
ALTER TABLE projects DROP COLUMN IF EXISTS cantidad_de_tokens;
ALTER TABLE projects DROP COLUMN IF EXISTS valor_nominal;
ALTER TABLE projects DROP COLUMN IF EXISTS smart_contract_address;
ALTER TABLE projects DROP COLUMN IF EXISTS financing_start_date;

-- Actualizar constraint de estados (nombre de columna cambiado)
ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE projects ADD CONSTRAINT chk_estado
    CHECK (estado IN ('PREPARACION', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'CANCELADO'));

-- Actualizar índices
DROP INDEX IF EXISTS idx_projects_status;
CREATE INDEX IF NOT EXISTS idx_projects_estado ON projects(estado);
