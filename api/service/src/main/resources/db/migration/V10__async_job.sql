CREATE TABLE async_job (
    id             TEXT PRIMARY KEY,
    data           TEXT NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

