ALTER TABLE ticket
    ADD COLUMN IF NOT EXISTS assigned_to TEXT,
    ADD COLUMN IF NOT EXISTS assigned_to_format TEXT NOT NULL DEFAULT 'plain',
    ADD COLUMN IF NOT EXISTS assigned_to_orphaned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS assigned_to_hash TEXT;

-- Index on hash for efficient assignee filtering (works with both plain and encrypted)
CREATE INDEX IF NOT EXISTS ticket_assigned_to_hash_idx ON ticket (assigned_to_hash);
