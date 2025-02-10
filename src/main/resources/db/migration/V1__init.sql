-- Enums
create table if not exists impact
(
    code       text primary key,
    label      text not null,
    deleted_at timestamptz
);

create table if not exists tag
(
    code       text primary key,
    label      text not null,
    deleted_at timestamptz
);


-- Ticket
create table if not exists query
(
    id         bigserial primary key,
    ts         text        not null,
    channel_id text        not null,
    date       timestamptz not null
);
create unique index query_ts_unique_idx on query (ts, channel_id);
create index query_date_idx on query (date);


create type ticket_status as enum (
    'opened', 'closed'
    );
create table if not exists ticket
(
    id                 bigserial primary key,
    query_id           bigint        not null,
    created_message_ts text,
    status             ticket_status not null,
    team               text,
    impact_code        text,

    constraint ticket_query_id_fk foreign key (query_id) references query (id),
    constraint ticket_impact_fk foreign key (impact_code) references impact (code)
);
create index ticket_query_id_idx on ticket (query_id);

create table if not exists ticket_to_tag
(
    ticket_id bigint not null,
    tag_code  text   not null,

    primary key (ticket_id, tag_code),
    constraint ticket_to_tag_ticket_id_fk foreign key (ticket_id) references ticket (id),
    constraint ticket_to_tag_tag_code_fk foreign key (tag_code) references tag (code)
);

create type ticket_event_type as enum (
    'opened','closed'
    );
create table if not exists ticket_log
(
    id        bigserial primary key,
    ticket_id bigint            not null,
    event     ticket_event_type not null,
    date      timestamptz       not null,

    constraint ticket_log_ticket_id_fk foreign key (ticket_id) references ticket (id)
);
create index ticket_log_ticket_id_idx on ticket_log (ticket_id);

-- Escalations
create type escalation_status as enum (
    'opened', 'resolved'
    );
create table if not exists escalation
(
    id                 bigserial primary key,
    ticket_id          bigint            not null,
    channel_id         text,
    thread_ts          text,
    created_message_ts text,
    status             escalation_status not null,
    team               text,

    constraint escalation_ticket_id_fk foreign key (ticket_id) references ticket (id),
    constraint escalation_thread_ts_unique unique (thread_ts, channel_id)
);
create unique index escalation_thread_ts_unique_idx on escalation (thread_ts, channel_id);

create table if not exists escalation_to_tag
(
    escalation_id bigint not null,
    tag_code      text   not null,

    primary key (escalation_id, tag_code),
    constraint escalation_to_tag_escalation_id_fk foreign key (escalation_id) references escalation (id),
    constraint escalation_to_tag_tag_code_fk foreign key (tag_code) references tag (code)
);

create type escalation_event_type as enum (
    'opened','resolved'
    );
create table if not exists escalation_log
(
    id            bigserial primary key,
    escalation_id bigint                not null,
    event         escalation_event_type not null,
    date          timestamptz           not null,

    constraint escalation_log_escalation_id_fk foreign key (escalation_id) references escalation (id)
);
create index escalation_log_escalation_id_idx on escalation_log (escalation_id);

