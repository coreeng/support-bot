ALTER TABLE analysis ADD COLUMN prompt_id TEXT;

CREATE INDEX analysis_prompt_id_idx ON analysis (prompt_id);
