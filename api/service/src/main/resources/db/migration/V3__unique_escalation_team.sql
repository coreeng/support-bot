CREATE UNIQUE INDEX escalation_open_unique
    ON escalation (ticket_id, team)
    WHERE status = 'opened';