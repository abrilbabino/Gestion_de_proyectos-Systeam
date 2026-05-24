-- Configuración de datos de prueba para testing del módulo de inversión
-- Asocia TKN-A con el proyecto 5 (FINANCIAMIENTO) y da saldo al usuario de prueba (id=52)

UPDATE subtokens SET proyecto_id = 5 WHERE nombre = 'TKN-A';

UPDATE users SET saldo_idea = 50000.00 WHERE id = 52;
