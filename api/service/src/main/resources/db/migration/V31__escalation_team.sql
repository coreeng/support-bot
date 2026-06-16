-- Escalation team history: code+label registry so renamed/removed escalation
-- team codes still resolve to a label on existing tickets/escalations (PT-518).
-- Active teams' group-ref / Slack mention still come from config; only code+label persisted.
create table if not exists escalation_team
(
    code       text primary key,
    label      text not null,
    deleted_at timestamptz
);
