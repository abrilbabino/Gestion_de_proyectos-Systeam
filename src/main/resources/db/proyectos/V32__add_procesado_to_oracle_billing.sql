ALTER TABLE oracle_billing ADD COLUMN procesado BOOLEAN DEFAULT FALSE;
UPDATE oracle_billing SET procesado = TRUE;
