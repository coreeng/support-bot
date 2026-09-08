-- Support Summary (EL-264): product tags are declared, not inferred from the label.
--
-- The Products View and the summary's product breakdown used to recognise product tags by the label
-- prefix "Product - <name>" — a business flag derived from mutable, user-facing text, duplicated in
-- the UI and the API. The flag is now `enums.tags[].product: true` in configuration and is synced
-- onto this column at startup with the label, so a retired product tag (no longer configured) keeps
-- its history attributed.
--
-- One-off backfill from the old convention for retired tags only, so their historical attribution
-- survives. Configured tags are not touched: the startup sync that follows this migration writes
-- the flag from configuration for every one of them, so configuration is the single source of truth.
-- (A tag dropped from configuration in this same rollout is only retired by that sync, after
-- Flyway, so it is not covered here; none is being removed in the rollout that ships this flag.)

ALTER TABLE tag
    ADD COLUMN IF NOT EXISTS product BOOLEAN NOT NULL DEFAULT false;

UPDATE tag
   SET product = true
 WHERE deleted_at IS NOT NULL
   AND label ~* '^\s*product\s*[-–—]\s*\S';
