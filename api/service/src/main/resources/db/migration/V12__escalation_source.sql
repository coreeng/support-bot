ALTER TABLE escalation ADD COLUMN source text NOT NULL DEFAULT 'manual';

ALTER TABLE escalation ADD CONSTRAINT escalation_source_check
    CHECK (source IN ('bot', 'manual'));
