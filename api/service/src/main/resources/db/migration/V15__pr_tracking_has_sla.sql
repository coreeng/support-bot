-- Tracks whether a PR was ever created with an SLA deadline.
-- Unlike sla_deadline (cleared on close), this is set at insert time and never modified.
-- This allows the insights query to correctly identify SLA-configured repos even after all their
-- PRs are closed and sla_deadline has been nulled out.
alter table pr_tracking
    add column has_sla boolean not null default false;

-- Back-fill: any row that still has a deadline or remaining time had an SLA when created.
-- Rows where both are null (no-SLA PRs, or closed PRs that cleared both fields) stay false.
--
-- KNOWN GAP — pre-V15 closed PRs stay has_sla=false permanently:
-- When a PR closes, updateStatus() nulls out both sla_deadline and sla_remaining, so any row
-- closed before this migration runs has lost the signal and is indistinguishable from a genuine
-- no-SLA PR here
update pr_tracking
    set has_sla = true
    where sla_deadline is not null or sla_remaining is not null;
