-- Erases one person from the troubleshooting session tables. How and when to run it:
-- docs/runbooks/troubleshooting-sessions.md. Pass '' for an identifier that is not known.

-- Events the person wrote keep their place in the log, but not who wrote them or what they said.
UPDATE troubleshooting_event
SET actor = 'erased',
    payload = COALESCE(
        (SELECT jsonb_object_agg(
                    field.key,
                    CASE
                        WHEN field.key IN ('text', 'reason', 'error') AND jsonb_typeof(field.value) = 'string'
                            THEN to_jsonb('[erased]'::text)
                        ELSE field.value
                    END)
         FROM jsonb_each(payload) AS field),
        payload)
WHERE lower(actor) = lower(:'email')
   OR actor = 'slack:' || :'slack_id';

-- Other events lose every mention of the person's email, in any letter case.
UPDATE troubleshooting_event
SET payload = regexp_replace(
        payload::text, regexp_replace(:'email', '([^[:alnum:]])', '\\\1', 'g'), '[erased]', 'gi')::jsonb
WHERE :'email' <> ''
  AND strpos(lower(payload::text), lower(:'email')) > 0;

-- And of the person's Slack id, as a whole word, which covers mentions such as <@U0123ABCD>.
UPDATE troubleshooting_event
SET payload = regexp_replace(payload::text, '\m' || :'slack_id' || '\M', 'erased', 'g')::jsonb
WHERE :'slack_id' ~ '^[A-Z0-9]+$'
  AND payload::text ~ ('\m' || :'slack_id' || '\M');
