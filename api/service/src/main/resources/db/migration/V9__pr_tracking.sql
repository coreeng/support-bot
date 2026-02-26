create type pr_tracking_status as enum ('OPEN', 'ESCALATED', 'CLOSED');

create table if not exists pr_tracking
(
    id            bigserial primary key,
    ticket_id     bigint             not null,
    github_repo   text               not null,
    pr_number     integer            not null,
    pr_created_at timestamptz        not null,
    sla_deadline  timestamptz        not null,
    owning_team   text               not null,
    status        pr_tracking_status not null default 'OPEN',
    escalation_id bigint,
    closed_at     timestamptz,
    created_at    timestamptz        not null default now(),

    constraint pr_tracking_ticket_id_fk foreign key (ticket_id) references ticket (id),
    constraint pr_tracking_escalation_id_fk foreign key (escalation_id) references escalation (id)
);
