ALTER TABLE users ADD COLUMN IF NOT EXISTS saldo_idea DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS saldo_usdt DECIMAL(15,2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS subtokens (
    id               BIGSERIAL PRIMARY KEY,
    nombre           VARCHAR(20) NOT NULL,
    suministro_total INTEGER NOT NULL,
    cupo_restante    INTEGER NOT NULL,
    precio_actual    DECIMAL(15,2) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS portfolio_activos (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES users(id),
    subtoken_id BIGINT NOT NULL REFERENCES subtokens(id),
    cantidad    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

UPDATE users SET saldo_idea = 10000.00, saldo_usdt = 5000.00 WHERE saldo_idea = 0;

INSERT INTO subtokens (nombre, suministro_total, cupo_restante, precio_actual) VALUES
    ('IDEA',  1000000, 750000, 1.50),
    ('TKN-A',   50000,  32000, 2.75),
    ('TKN-B',  100000,  88000, 0.85);

INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad)
SELECT u.id, s.id, 250
FROM users u, subtokens s
WHERE u.id = (SELECT MIN(id) FROM users)
  AND s.nombre = 'TKN-A';

INSERT INTO portfolio_activos (usuario_id, subtoken_id, cantidad)
SELECT u.id, s.id, 100
FROM users u, subtokens s
WHERE u.id = (SELECT MIN(id) FROM users)
  AND s.nombre = 'TKN-B';
