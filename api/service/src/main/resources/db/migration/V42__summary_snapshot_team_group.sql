-- Support Summary team groups (ADR-010): the prose is cached per (window, team group, prompt version).
--
-- `team_group` is the group's code, or '' for a summary of the whole window ("All"). It is NOT NULL
-- with a '' sentinel rather than nullable because NULLs are distinct in a unique constraint, so
-- ON CONFLICT would never match an "All" row. A group's code is never empty (TeamGroup rejects it),
-- so it can never collide with ''. Existing rows become "All" with their fingerprints unchanged, so
-- they keep being served after the upgrade.
--
-- Rolling back to a build before this migration needs the constraint restored by hand — see
-- docs/runbooks/support-summary.md.

ALTER TABLE summary_snapshot
    ADD COLUMN IF NOT EXISTS team_group TEXT NOT NULL DEFAULT '';

ALTER TABLE summary_snapshot
    DROP CONSTRAINT IF EXISTS summary_snapshot_window_prompt_unique;
ALTER TABLE summary_snapshot
    DROP CONSTRAINT IF EXISTS summary_snapshot_window_group_prompt_unique;
ALTER TABLE summary_snapshot
    ADD CONSTRAINT summary_snapshot_window_group_prompt_unique UNIQUE (window_from, window_to, team_group, prompt_id);
