ALTER TABLE investments ADD COLUMN IF NOT EXISTS descuento_porcentaje INTEGER DEFAULT 0;

-- Actualizar inversiones existentes que tuvieron descuento (ej. pagaron menos del precio base por token)
UPDATE investments i
SET descuento_porcentaje = 5
FROM subtokens s
WHERE i.proyecto_id = s.proyecto_id
  AND i.monto_idea < (s.precio_base * i.sub_tokens_recibidos);
