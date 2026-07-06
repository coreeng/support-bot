-- Code-owner repos chase the maintaining team to merge after the code owners approve, and close only
-- on the real merge. Two new lifecycle states (see PrLifecycle.TRANSITIONS):
--   AWAITING_MERGE  -- code-owner-approved + mergeable; chasing the owning team to merge
--   MERGE_ESCALATED -- merge SLA breached while awaiting the merge
-- Enum values are append-only; mirrors V13 which added CHANGES_REQUESTED / APPROVED.
alter type pr_tracking_status add value if not exists 'AWAITING_MERGE';
alter type pr_tracking_status add value if not exists 'MERGE_ESCALATED';
