-- A small, non-business table proves that versioned migrations reached PostgreSQL.
-- Product, account, and other domain tables belong to later phases.
CREATE TABLE foundation_metadata (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    phase VARCHAR(32) NOT NULL CHECK (phase = 'foundation'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO foundation_metadata (id, phase) VALUES (1, 'foundation');
