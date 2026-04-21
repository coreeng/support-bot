-- Per-row record of whether a PR was created with an SLA deadline.
-- Unlike sla_deadline (cleared on close), this is set at insert time and never modified, so the
-- per-PR truth survives the close operation.
--
-- The repos-health dashboard does NOT read this column: it drives hasSla off current SLA config
-- (see TenantInsightsController#replaceHasSlaWithCurrentConfig), so badge state always reflects
-- present-day configuration rather than historical data. This column is read by the in-flight-prs
-- endpoint (per-PR granularity) and remains useful as a durable per-row fact for future features.
alter table pr_tracking
    add column has_sla boolean not null default false;

-- Back-fill: any row that still has a deadline or remaining time had an SLA when created.
-- Rows where both are null (no-SLA PRs, or closed PRs that cleared both fields) stay false.
-- Pre-V15 closures permanently lose their per-row signal — acceptable because the repos-health
-- tab ignores this column, and closed PRs do not surface on the in-flight-prs tab under today's
-- lifecycle (no CLOSED → OPEN transition path exists; re-opening would need a dedicated migration
-- step to re-derive has_sla from config).
update pr_tracking
    set has_sla = true
    where sla_deadline is not null or sla_remaining is not null;
