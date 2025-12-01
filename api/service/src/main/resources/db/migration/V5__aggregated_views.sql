DROP VIEW IF EXISTS aggregated_ticket_data CASCADE;
CREATE VIEW aggregated_ticket_data AS
SELECT t.id AS ticket_id,
       t.status,
       t.impact_code AS impact,
       q.id AS query_id,
       q.ts AS query_msg_ts,
       q.channel_id AS query_channel_id,
       q.date AS query_posted_ts,
       ( SELECT min(ticket_log.date) AS min
            FROM ticket_log
            WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'opened'::ticket_event_type) AS first_open_ts,
    ( SELECT max(ticket_log.date) AS max
            FROM ticket_log
            WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'closed'::ticket_event_type) AS last_closed_ts,
    ( SELECT max(ticket_log.date) AS max
            FROM ticket_log
            WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'opened'::ticket_event_type) AS last_open_ts,
    ( SELECT max(ticket_log.date) AS max
            FROM ticket_log
            WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'stale'::ticket_event_type) AS last_stale_ts,
    ( SELECT array_agg(tag.label) AS array_agg
            FROM ticket_to_tag ttt
            JOIN tag ON tag.code = ttt.tag_code
            WHERE ttt.ticket_id = t.id) AS tags,
    ( SELECT array_agg(tag.code) AS array_agg
            FROM ticket_to_tag ttt
            JOIN tag ON tag.code = ttt.tag_code
            WHERE ttt.ticket_id = t.id) AS tags_ids
FROM query q
LEFT JOIN ticket t ON t.query_id = q.id;

DROP VIEW IF EXISTS aggregated_escalation_data CASCADE;
CREATE VIEW aggregated_escalation_data AS
SELECT e.id AS escalation_id,
       e.ticket_id AS ticket_id,
       e.team AS team_id,
       e.status AS status,
       ( SELECT min(l.date) AS min
            FROM escalation_log l
            WHERE l.escalation_id = e.id AND l.event = 'opened') AS open_ts,
    ( SELECT max(l.date) AS max
            FROM escalation_log l
            WHERE l.escalation_id = e.id AND l.event = 'resolved') AS resolved_ts,
    ( SELECT array_agg(tag.code) AS array_agg
            FROM escalation_to_tag ett
            JOIN tag ON tag.code = ett.tag_code
            WHERE ett.escalation_id = e.id) AS tag_ids,
    ( SELECT array_agg(tag.label) AS array_agg
            FROM escalation_to_tag ett
            JOIN tag ON tag.code = ett.tag_code
            WHERE ett.escalation_id = e.id) AS tags
FROM escalation e;

DROP VIEW IF EXISTS weekly_tag_counts;
CREATE VIEW weekly_tag_counts AS
SELECT date_trunc('week', first_open_ts) AS week,
       unnest(tags)                      AS tag,
       count(*)                          AS tagcount
FROM aggregated_ticket_data
GROUP BY week, tags;

CREATE OR REPLACE FUNCTION business_time_between(
    start_ts timestamptz,
    end_ts   timestamptz,
    tz       text,                 -- e.g. 'Europe/London'
    work_start time DEFAULT '09:00',
    work_end   time DEFAULT '17:00'
)
    RETURNS interval
    LANGUAGE sql
AS $$
WITH bounds AS (
    -- convert to *local* timestamp in the passed timezone
    SELECT
        (LEAST(start_ts, end_ts) AT TIME ZONE tz) AS s,
        (GREATEST(start_ts, end_ts) AT TIME ZONE tz) AS e
),
     days AS (
         SELECT
             d::date AS d
         FROM bounds b,
              generate_series(
                      date_trunc('day', b.s)::date,
                      date_trunc('day', b.e)::date,
                      interval '1 day'
              ) AS d
     ),
     work_spans AS (
         SELECT
             GREATEST(d + work_start, b.s) AS s,
             LEAST(d + work_end, b.e) AS e
         FROM days, bounds b
         WHERE EXTRACT(ISODOW FROM d) BETWEEN 1 AND 5
     ),
     valid_spans AS (
         SELECT (e - s) AS span
         FROM work_spans
         WHERE e > s
     )
SELECT COALESCE(SUM(span), interval '0') FROM valid_spans;
$$;
