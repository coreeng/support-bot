CREATE TABLE async_job (
    id             TEXT PRIMARY KEY,
    data           TEXT NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX async_job_id_idx ON async_job (id);

