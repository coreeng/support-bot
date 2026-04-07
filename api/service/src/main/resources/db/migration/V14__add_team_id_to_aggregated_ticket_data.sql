CREATE OR REPLACE VIEW aggregated_ticket_data AS
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
            WHERE ttt.ticket_id = t.id) AS tags_ids,
       t.team AS team_id
FROM query q
LEFT JOIN ticket t ON t.query_id = q.id;