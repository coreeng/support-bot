-- Add anonymous_id column to ticket_ratings table for duplicate prevention
-- This is a hash of ticket_id + user_id to prevent duplicate ratings

ALTER TABLE ticket_ratings 
ADD COLUMN anonymous_id VARCHAR(64) NOT NULL DEFAULT '';
