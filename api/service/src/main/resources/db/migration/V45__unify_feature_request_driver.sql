-- Use the same driver label in the definition, guidance, and output format.
-- Keep version 2 intact so its prompt ID still identifies the original classifications.
INSERT INTO analysis_prompt (type, version, content, is_in_use)
VALUES ('classification', 3, (
    SELECT replace(content, 'Feature Request', 'Feature or Enhancement Request')
    FROM analysis_prompt
    WHERE type = 'classification' AND version = 2
), FALSE);

-- Unflag the old prompt first to satisfy the one-in-use index.
UPDATE analysis_prompt SET is_in_use = FALSE WHERE type = 'classification' AND is_in_use;
UPDATE analysis_prompt SET is_in_use = TRUE WHERE type = 'classification' AND version = 3;
