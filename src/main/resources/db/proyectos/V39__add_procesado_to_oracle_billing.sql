ALTER TABLE oracle_billing ADD COLUMN IF NOT EXISTS procesado BOOLEAN DEFAULT FALSE;
UPDATE oracle_billing SET procesado = TRUE;
