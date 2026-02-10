CREATE TABLE analysis
(
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL,
    driver TEXT NOT NULL,
    category TEXT NOT NULL,
    feature TEXT NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_analysis_ticket_id ON analysis(ticket_id);
CREATE INDEX idx_analysis_driver ON analysis(driver);
