-- This table captures user feedback that satisfies anonymity
-- We are creating columns that exist in other tables but this is expected behaviour

CREATE TABLE ticket_ratings (
    -- Unique identifier for each rating to ensure anonymity and prevent guessing e.g. by sequence
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    submitted_ts TEXT NOT NULL,

    -- I'm adding snapshot prefix to make it clear that we're copying these values that exist in other tables for anonymity
    status ticket_status NOT NULL, -- Using existing enum type
    impact TEXT REFERENCES impact(code), -- Foreign key to impact table

    tags VARCHAR(255)[], -- Array of tags at time of rating
    escalated_teams VARCHAR(255)[] -- Array of teams ticket was escalated to
);