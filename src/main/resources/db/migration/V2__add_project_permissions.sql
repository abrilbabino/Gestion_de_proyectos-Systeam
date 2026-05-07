INSERT INTO permissions (name, description) VALUES
    ('project:read',    'Ver proyectos'),
    ('project:update',  'Editar proyectos'),
    ('project:delete',  'Eliminar proyectos'),
    ('project:invest',  'Invertir en proyectos');

UPDATE roles SET name = 'CREATOR' WHERE name = 'CREATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CREATOR'
  AND p.name IN ('project:read', 'project:update');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'INVESTOR'
  AND p.name IN ('project:read', 'project:invest');
