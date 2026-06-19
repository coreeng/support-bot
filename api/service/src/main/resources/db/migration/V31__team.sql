create table if not exists team
(
    code       text primary key,
    label      text not null,
    deleted_at timestamptz
);
