alter type pr_tracking_status add value if not exists 'CHANGES_REQUESTED';
alter type pr_tracking_status add value if not exists 'APPROVED';

alter table pr_tracking
    add column sla_remaining           interval,
    add column last_review_at          timestamptz,
    add column last_author_activity_at timestamptz;

alter table pr_tracking
    alter column sla_deadline drop not null;

-- SLA clock consistency: at most one of deadline/remaining is set at any time.
-- Both null is permitted for closed records where the SLA fields are cleared.
alter table pr_tracking add constraint chk_sla_clock_consistency
    check (
        (sla_deadline is not null and sla_remaining is null)
        or (sla_deadline is null and sla_remaining is not null)
        or (sla_deadline is null and sla_remaining is null)
    );
