-- Support Summary (EL-264): product tags are declared, not inferred from the label.
--
-- The Products View and the summary's product breakdown used to recognise product tags by the label
-- prefix "Product - <name>" — a business flag derived from mutable, user-facing text, duplicated in
-- the UI and the API. The flag is now `enums.tags[].product: true` in configuration and is synced
-- onto this column at startup with the label, so a retired product tag (no longer configured) keeps
-- its history attributed.
--
-- One-off backfill from the old convention, so existing deployments keep their products until the
-- flag is set in config; from then on the startup sync is authoritative for every configured tag.

ALTER TABLE tag
    ADD COLUMN IF NOT EXISTS product BOOLEAN NOT NULL DEFAULT false;

UPDATE tag
   SET product = true
 WHERE label ~* '^\s*product\s*[-–—]\s*\S';
