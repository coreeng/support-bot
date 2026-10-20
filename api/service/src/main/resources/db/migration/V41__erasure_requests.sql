-- Erasure Requests accepted from Elevate's erasure orchestrator (ADR-009). The id is the orchestrator's
-- request id and the idempotency key, so it is supplied rather than generated.
CREATE TABLE IF NOT EXISTS erasure_requests
(
    id                 UUID PRIMARY KEY,
    current_user_email VARCHAR(255) NOT NULL,
    new_user_email     VARCHAR(255) NOT NULL,
    status             TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT erasure_requests_status_check CHECK (status IN ('IN_PROGRESS'))
);
