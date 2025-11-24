DROP VIEW IF EXISTS aggregated_ticket_data CASCADE;
CREATE VIEW aggregated_ticket_data AS
SELECT t.id          AS ticket_id,
       t.status,
       t.impact_code,
       i.label       AS impact,
       q.date        AS last_reply_date,
       q.id          AS query_id,
       q.ts          AS query_msg_ts,
       q.channel_id  AS query_channel_id,
       q.date        AS query_posted_ts,
       (SELECT min(ticket_log.date) AS min
        FROM ticket_log
        WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'opened'::ticket_event_type
        ) AS first_open_ts,
       (SELECT max (ticket_log.date) AS max
        FROM ticket_log
        WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'closed'::ticket_event_type
        ) AS last_closed_ts,
       (SELECT max (ticket_log.date) AS max
        FROM ticket_log
        WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'opened'::ticket_event_type
        ) AS last_open_ts,
       (SELECT max (ticket_log.date) AS max
        FROM ticket_log
        WHERE ticket_log.ticket_id = t.id AND ticket_log.event = 'stale'::ticket_event_type
        ) AS last_stale_ts,
       (SELECT array_agg(tag.label) AS array_agg
        FROM ticket_to_tag ttt
        JOIN tag ON tag.code = ttt.tag_code
        WHERE ttt.ticket_id = t.id
        ) AS tags,
       (SELECT array_agg(tag.code) AS array_agg
        FROM ticket_to_tag ttt
        JOIN tag ON tag.code = ttt.tag_code
        WHERE ttt.ticket_id = t.id
        ) AS tags_ids
FROM query q
LEFT JOIN ticket t ON t.query_id = q.id
LEFT JOIN impact i on t.impact_code = i.code;

DROP VIEW IF EXISTS aggregated_escalation_data CASCADE;
CREATE VIEW aggregated_escalation_data AS
SELECT e.id        AS escalation_id,
       e.ticket_id AS ticket_id,
       e.team      AS team_id,
       e.status    AS status,
       (SELECT min(l.date) AS min
        FROM escalation_log l
        WHERE l.escalation_id = e.id AND l.event = 'opened'
        ) AS open_ts,
       (SELECT max (l.date) AS max
        FROM escalation_log l
        WHERE l.escalation_id = e.id AND l.event = 'resolved'
        ) AS resolved_ts,
       (SELECT array_agg(tag.code) AS array_agg
        FROM escalation_to_tag ett
        JOIN tag ON tag.code = ett.tag_code
        WHERE ett.escalation_id = e.id
        ) AS tag_ids,
       (SELECT array_agg(tag.label) AS array_agg
        FROM escalation_to_tag ett
        JOIN tag ON tag.code = ett.tag_code
        WHERE ett.escalation_id = e.id
        ) AS tags
FROM escalation e;

DROP VIEW IF EXISTS weekly_tag_counts;
CREATE VIEW weekly_tag_counts AS
SELECT date_trunc('week', first_open_ts) AS week,
       unnest(tags)                      AS tag,
       count(*)                          AS tagcount
FROM aggregated_ticket_data
GROUP BY week, tags;

DROP VIEW IF EXISTS working_hours;
CREATE VIEW working_hours AS
SELECT hour_ts FROM support_calendar WHERE work_hour is TRUE;

DROP VIEW IF EXISTS ticket_working_hours_summary;
CREATE VIEW ticket_working_hours_summary AS
SELECT ticket_id,
       3600 * (SELECT count(*) FROM working_hours WHERE hour_ts >= first_open_ts AND hour_ts <  last_closed_ts) AS duration,
       3600 * (SELECT count(*) FROM working_hours WHERE hour_ts >= escalation_open_ts AND hour_ts <  COALESCE(last_closed_ts, now())) AS escalation_duration,
       3600 * (SELECT count(*) FROM working_hours WHERE hour_ts >= query_posted_ts AND hour_ts <  first_open_ts) AS duration_to_first_response

FROM (SELECT t.ticket_id,
             query_posted_ts,
             first_open_ts,
             last_closed_ts,
             open_ts as escalation_open_ts
      FROM aggregated_ticket_data t
               LEFT JOIN aggregated_escalation_data e ON t.ticket_id = e.ticket_id) timestamps;

DROP VIEW IF EXISTS escalation_working_hours_summary;
CREATE VIEW escalation_working_hours_summary AS
SELECT escalation_id,
       3600 * (SELECT count(*) FROM working_hours WHERE hour_ts >= open_ts AND hour_ts <  COALESCE(resolved_ts, now())) AS escalation_duration
FROM (SELECT escalation_id,
             open_ts,
             resolved_ts
      FROM aggregated_escalation_data) timestamps;
