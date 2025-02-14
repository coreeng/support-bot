alter table ticket add column last_interacted_at timestamptz not null default now();

alter type ticket_status add value if not exists 'stale' after 'opened';
alter type ticket_event_type add value if not exists 'stale' after 'opened';
