-- Optional follow-up feedback captured after a rating (see docs/adr/adr-009-rating-feedback-anonymity.md).
-- No names or Slack IDs are ever stored. ticket_id is only set when the submitter explicitly opts in
-- to sharing their name; anonymous feedback stays as unlinked as the rating itself.
ALTER TABLE ratings
    ADD COLUMN IF NOT EXISTS feedback TEXT,
    ADD COLUMN IF NOT EXISTS ticket_id BIGINT REFERENCES ticket (id);
