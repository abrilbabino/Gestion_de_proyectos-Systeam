-- ADMIN debe tener todos los permisos de proyectos que se agregaron en V2__add_project_permissions.sql
-- pero no se asignaron al rol ADMIN en ese momento.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('project:read', 'project:update', 'project:delete', 'project:invest')
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
