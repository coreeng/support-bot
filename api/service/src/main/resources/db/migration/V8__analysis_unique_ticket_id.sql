-- Add unique constraint on ticket_id for analysis table
-- This allows upsert operations based on ticket_id

-- First, remove any duplicate ticket_ids (keep the most recent one)
DELETE FROM analysis a1
USING analysis a2
WHERE a1.ticket_id = a2.ticket_id
  AND a1.id < a2.id;

-- Drop the existing index
DROP INDEX IF EXISTS idx_analysis_ticket_id;

-- Add unique constraint on ticket_id
ALTER TABLE analysis ADD CONSTRAINT analysis_ticket_id_unique UNIQUE (ticket_id);

