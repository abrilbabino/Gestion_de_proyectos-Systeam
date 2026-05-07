CREATE TABLE projects (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT NOT NULL,
    required_amount       NUMERIC(15, 2) NOT NULL,
    current_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status                VARCHAR(20) NOT NULL DEFAULT 'PREPARACION',
    creator_id            BIGINT NOT NULL,
    cantidad_de_tokens    BIGINT,
    valor_nominal         NUMERIC(15, 2),
    smart_contract_address VARCHAR(255),
    financing_start_date  TIMESTAMP,
    financing_end_date    TIMESTAMP,
    
    gobernanza_comunidad  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMP,
    
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_project_creator FOREIGN KEY (creator_id) REFERENCES users(id),
    CONSTRAINT chk_status CHECK (status IN ('PREPARACION', 'FINANCIAMIENTO', 'EJECUCION', 'FINALIZADO', 'CANCELADO')), -- Agregué CANCELADO por si acaso
    CONSTRAINT chk_required_amount CHECK (required_amount > 0),
    CONSTRAINT chk_current_amount CHECK (current_amount >= 0),
    CONSTRAINT chk_tokens_positive CHECK (cantidad_de_tokens IS NULL OR cantidad_de_tokens > 0),
    CONSTRAINT chk_valor_nominal_positive CHECK (valor_nominal IS NULL OR valor_nominal > 0)
);

CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_creator_id ON projects(creator_id);
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);
CREATE INDEX idx_projects_not_deleted ON projects(id) WHERE deleted_at IS NULL;