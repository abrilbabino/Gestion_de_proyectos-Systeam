-- Add AUDITADO to projects status check constraint
ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_estado;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS proyectos_estado_check;

ALTER TABLE projects ADD CONSTRAINT proyectos_estado_check
    CHECK (estado IN ('PREPARACION', 'EN_AUDITORIA', 'AUDITADO', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'RECHAZADO', 'CANCELADO'));

-- Insert AUDITOR role
INSERT INTO roles (name, description)
VALUES ('AUDITOR', 'Rol de Auditor')
ON CONFLICT (name) DO NOTHING;

-- Grant project:audit to AUDITOR role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'AUDITOR' AND p.name = 'project:audit'
ON CONFLICT DO NOTHING;
