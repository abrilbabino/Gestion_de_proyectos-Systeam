-- V46: Enhance audit_findings table with risk and viability scores

ALTER TABLE audit_findings ADD COLUMN risk_score VARCHAR(2);
ALTER TABLE audit_findings ADD COLUMN financial_viability_score INTEGER;

-- Update the CHECK constraint on resultado to allow NECESITA_CAMBIOS
ALTER TABLE audit_findings DROP CONSTRAINT audit_findings_resultado_check;

ALTER TABLE audit_findings ADD CONSTRAINT audit_findings_resultado_check
    CHECK (resultado IN ('APROBADO', 'RECHAZADO', 'NECESITA_CAMBIOS'));
