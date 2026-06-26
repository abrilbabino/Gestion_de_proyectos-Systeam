-- Add KYC fields to users table
ALTER TABLE users ADD COLUMN kyc_status VARCHAR(50) DEFAULT 'PENDING';
ALTER TABLE users ADD COLUMN kyc_provider_id VARCHAR(255);

-- Create project_audit table
CREATE TABLE project_audit (
    id SERIAL PRIMARY KEY,
    proyecto_id INTEGER NOT NULL REFERENCES projects(id),
    auditor_id INTEGER NOT NULL REFERENCES users(id),
    risk_score VARCHAR(5) NOT NULL,
    financial_viability_score INTEGER NOT NULL,
    observaciones TEXT,
    dictamen VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
