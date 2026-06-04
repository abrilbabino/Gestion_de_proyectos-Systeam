CREATE TABLE oracle_billing (
    id              SERIAL PRIMARY KEY,
    proyecto_id     BIGINT NOT NULL,
    monto_facturado NUMERIC(38, 18) NOT NULL,
    fecha_reporte   TIMESTAMP NOT NULL,
    oracle_address  VARCHAR(42) NOT NULL,
    tx_hash         VARCHAR(66) NOT NULL,

    CONSTRAINT fk_oracle_billing_proyecto FOREIGN KEY (proyecto_id) REFERENCES projects(id),
    CONSTRAINT oracle_billing_tx_hash_unique UNIQUE (tx_hash)
);

CREATE INDEX idx_oracle_billing_proyecto_id ON oracle_billing(proyecto_id);
CREATE INDEX idx_oracle_billing_fecha_reporte ON oracle_billing(fecha_reporte DESC);
