ALTER TABLE escalation ADD COLUMN source text NOT NULL DEFAULT 'manual';

-- Backfill: any escalation linked from pr_tracking was created by the bot
UPDATE escalation SET source = 'bot'
WHERE id IN (SELECT escalation_id FROM pr_tracking WHERE escalation_id IS NOT NULL);
