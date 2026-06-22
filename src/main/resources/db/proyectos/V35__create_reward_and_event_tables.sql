-- V31: Create reward ledger, eventos, event attendance, and vote economics config tables

CREATE TABLE IF NOT EXISTS vote_economics_config (
    id INT PRIMARY KEY DEFAULT 1,
    vote_cost DECIMAL(40,18) NOT NULL DEFAULT 1.0,
    vote_reward DECIMAL(40,18) NOT NULL DEFAULT 0.5,
    treasury_user_id BIGINT REFERENCES users(id),
    CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS reward_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(30) NOT NULL,
    ref_type VARCHAR(20) NOT NULL,
    ref_id BIGINT NOT NULL,
    tx_hash VARCHAR(66),
    amount DECIMAL(40,18) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (reason, ref_type, ref_id, user_id)
);

CREATE TABLE IF NOT EXISTS eventos (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    fecha_evento TIMESTAMP NOT NULL,
    reward_amount DECIMAL(40,18) NOT NULL DEFAULT 0,
    proyecto_id BIGINT REFERENCES projects(id),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evento_asistencias (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL REFERENCES eventos(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    confirmed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (evento_id, user_id)
);

-- Seed default vote economics config (cost=1, reward=0.5, no treasury user yet)
INSERT INTO vote_economics_config (id, vote_cost, vote_reward, treasury_user_id)
VALUES (1, 1.0, 0.5, NULL)
ON CONFLICT (id) DO NOTHING;

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_reward_ledger_user_id ON reward_ledger(user_id);
CREATE INDEX IF NOT EXISTS idx_reward_ledger_ref ON reward_ledger(ref_type, ref_id);
CREATE INDEX IF NOT EXISTS idx_evento_asistencias_evento ON evento_asistencias(evento_id);
CREATE INDEX IF NOT EXISTS idx_evento_asistencias_user ON evento_asistencias(user_id);
