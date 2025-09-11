-- This table captures user feedback
-- We are creating columns that exist in other tables but this is expected behaviour for snapshot data
CREATE TABLE IF NOT EXISTS ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    submitted_ts TEXT NOT NULL,

    status ticket_status NOT NULL,
    impact TEXT REFERENCES impact(code),

    tags VARCHAR(255)[], 
    is_escalated BOOLEAN NOT NULL DEFAULT FALSE -- Whether the ticket was escalated
);

-- Add rating_submitted column to tickets table to track if a rating has been submitted
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS rating_submitted BOOLEAN NOT NULL DEFAULT FALSE;
