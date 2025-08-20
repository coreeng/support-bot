-- Add rating_submitted column to tickets table to track if a rating has been submitted
ALTER TABLE ticket ADD COLUMN rating_submitted BOOLEAN NOT NULL DEFAULT FALSE;

-- Drop the anonymous_id column from ratings table since we're no longer using pseudo-anonymity
ALTER TABLE ratings DROP COLUMN anonymous_id;
