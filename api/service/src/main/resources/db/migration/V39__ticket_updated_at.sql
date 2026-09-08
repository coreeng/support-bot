-- Support Summary (EL-264): a trigger-maintained `ticket.updated_at`.
--
-- The summary cache is validated by a fingerprint of the window's data. The part of the report that
-- is attributed by ticket rather than by analysis (team, status, product tags) used to be digested
-- with an MD5 over every ticket in the window on every poll of GET /summary. A timestamp that moves
-- when a ticket row or its tags change lets the fingerprint read `max(updated_at)` instead.
--
-- `last_interacted_at` is deliberately excluded from the change detection: it tracks Slack thread
-- activity and is bumped on every message, which would regenerate the (LLM-produced) summary on chatter
-- that changes nothing the prose describes. Everything else on the row counts as an edit.

ALTER TABLE ticket
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill: the last Slack interaction is the closest thing the row has to a modification time.
UPDATE ticket
   SET updated_at = COALESCE(last_interacted_at, now());

CREATE OR REPLACE FUNCTION ticket_set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT'
        OR (to_jsonb(NEW) - 'updated_at' - 'last_interacted_at')
           IS DISTINCT FROM (to_jsonb(OLD) - 'updated_at' - 'last_interacted_at') THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS ticket_set_updated_at ON ticket;
CREATE TRIGGER ticket_set_updated_at
    BEFORE INSERT OR UPDATE ON ticket
    FOR EACH ROW
EXECUTE FUNCTION ticket_set_updated_at();

-- Tag edits live on ticket_to_tag, so they have to reach the parent row explicitly. On an UPDATE that
-- moves a row between tickets both parents are touched.
CREATE OR REPLACE FUNCTION ticket_to_tag_touch_ticket()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE ticket SET updated_at = now() WHERE id = NEW.ticket_id;
    END IF;
    IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.ticket_id <> NEW.ticket_id) THEN
        UPDATE ticket SET updated_at = now() WHERE id = OLD.ticket_id;
    END IF;
    RETURN NULL;
END
$$;

DROP TRIGGER IF EXISTS ticket_to_tag_touch_ticket ON ticket_to_tag;
CREATE TRIGGER ticket_to_tag_touch_ticket
    AFTER INSERT OR UPDATE OR DELETE ON ticket_to_tag
    FOR EACH ROW
EXECUTE FUNCTION ticket_to_tag_touch_ticket();
