ALTER TABLE ticket
    ADD COLUMN IF NOT EXISTS assigned_to TEXT,
    ADD COLUMN IF NOT EXISTS assigned_to_format TEXT NOT NULL DEFAULT 'plain',
    ADD COLUMN IF NOT EXISTS assigned_to_orphaned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS ticket_assigned_to_idx ON ticket (assigned_to);


