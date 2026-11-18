-- Troubleshooting sessions (ADR-012): one session per ticket and its append-only event log.
-- last_seq is bumped with UPDATE ... RETURNING in the same transaction as each insert, so the row
-- lock orders concurrent appends and a reader following "seq > N" never skips an event.
CREATE TABLE troubleshooting_session (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id),
    mode       TEXT        NOT NULL DEFAULT 'MANUAL',
    last_seq   BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT troubleshooting_session_ticket_unique UNIQUE (ticket_id)
);

CREATE TABLE troubleshooting_event (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES troubleshooting_session (id),
    seq        BIGINT      NOT NULL,
    type       TEXT        NOT NULL,
    actor      TEXT        NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT troubleshooting_event_seq_unique UNIQUE (session_id, seq)
);
