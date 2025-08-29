CREATE UNIQUE INDEX escalation_open_unique
    ON escalation (ticket_id, team)
    WHERE status = 'opened';

ALTER TABLE escalation
DROP CONSTRAINT IF EXISTS escalation_thread_ts_unique;

DROP INDEX IF EXISTS escalation_thread_ts_unique_idx;