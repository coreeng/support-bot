-- Anonymous ticket ratings table for support feedback
-- This table captures user feedback while maintaining anonymity
-- No direct foreign keys to preserve privacy

CREATE TABLE ticket_ratings (
    -- Unique identifier for each rating to ensure anonymity and prevent sequence guessing
    rating_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Rating data 
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    
    -- Timestamp when rating was submitted
    rating_submitted_ts TEXT NOT NULL,
    rating_submitted_ts_iso TIMESTAMP WITH TIME ZONE,
    
    -- Snapshot data for analytics (captured at time of rating)
    -- These are copies of data, not foreign keys, to maintain anonymity
    -- Added snapshot suffix to indicate these are static at time of rating and not live references
    ticket_status_snapshot ticket_status NOT NULL, -- Using existing enum type
    ticket_impact_snapshot TEXT, -- Howcome this is not an enum that we can reuse?
    
    -- Team/category context (without revealing specific assignee)
    -- Making sure to not use same names as in tickets table to avoid confusion and maintain anonymity
    TAG_SNAPSHOT VARCHAR(255), -- Most relevant tag at time of rating (matches tags.label column)
    escalated BOOLEAN DEFAULT FALSE, -- Was ticket escalated to a team? We can't use escalation assignee due to anonymity

    -- Week/month for time-based analytics (anonymized temporal data)
    rating_week DATE, -- Monday of the week when rating was submitted
    rating_month DATE -- First day of month when rating was submitted
);

-- Timestamp conversion trigger
CREATE OR REPLACE FUNCTION update_rating_ts_iso()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.rating_submitted_ts_iso := TO_TIMESTAMP(NEW.rating_submitted_ts::double precision);
    NEW.rating_week := DATE_TRUNC('week', NEW.rating_submitted_ts_iso);
    NEW.rating_month := DATE_TRUNC('month', NEW.rating_submitted_ts_iso);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_rating_ts_iso_trigger
    BEFORE INSERT ON ticket_ratings
    FOR EACH ROW
EXECUTE FUNCTION update_rating_ts_iso();