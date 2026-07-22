-- True once a record has ever reached AWAITING_MERGE (never unset). Lets the code tell a paused
-- review-wait clock apart from a paused merge-wait clock — they share the same remaining-time
-- column, and without this flag nothing records which one a stored value belongs to.
-- Set atomically by JdbcPrTrackingRepository#enterMergePhase. No backfill needed: this feature
-- hasn't shipped yet, so no existing row can be affected.
alter table pr_tracking add column if not exists merge_phase_entered boolean not null default false;
