-- Add rating_submitted column to tickets table to track if a rating has been submitted
ALTER TABLE ticket ADD COLUMN rating_submitted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ratings DROP COLUMN anonymous_id;
