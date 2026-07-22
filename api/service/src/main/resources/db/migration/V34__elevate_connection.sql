CREATE TABLE elevate_products (
    resource_id TEXT PRIMARY KEY,
    payload JSONB NOT NULL
);

CREATE TABLE elevate_users (
    resource_id UUID PRIMARY KEY,
    payload JSONB NOT NULL
);

CREATE TABLE elevate_journeys (
    resource_id TEXT PRIMARY KEY,
    payload JSONB NOT NULL
);

CREATE TABLE elevate_sync_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    last_ping_attempt_at TIMESTAMPTZ,
    last_ping_success_at TIMESTAMPTZ,
    last_ping_succeeded BOOLEAN,
    last_ping_error TEXT,
    last_sync_attempt_at TIMESTAMPTZ,
    last_sync_success_at TIMESTAMPTZ,
    last_sync_succeeded BOOLEAN,
    last_sync_error TEXT
);

INSERT INTO elevate_sync_state (singleton) VALUES (TRUE);
