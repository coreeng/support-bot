# Runbook: troubleshooting sessions

A troubleshooting session ([ADR-012](../adr/adr-012-ai-troubleshooting-sessions.md)) is an
append-only log of events for one ticket, kept in two tables: `troubleshooting_session` (one row per
ticket) and `troubleshooting_event` (the log, ordered by `seq`). Settings are in
[`api/service/docs/configuration.md`](../../api/service/docs/configuration.md#troubleshooting-sessions).

## Where personal data is

`troubleshooting_session` holds none. In `troubleshooting_event`, personal data is only in these
places, which is what `erasure-compliance.yaml` lists as `anonymised`:

- **`actor`**: who the event is from. `slack:<user id>` for a message in the query's Slack thread
  (`slack:bot:<bot id>` for a bot), or `system`.
- **Free text in `payload`**: the `text` field. A person's own words are in the events whose
  `actor` is that person. Anyone's text can mention someone else, as an email address or as a
  Slack mention such as `<@U0123ABCD>`.

No payload field holds a user id or an email on its own: who an event is from is only in `actor`.

| Event type | `actor` | Payload fields with free text |
|---|---|---|
| `SLACK_MESSAGE` | `slack:<user id>`, or `system` when the thread could not be read | `text` |

## Erasing one person

Run [`troubleshooting-sessions.sql`](../../api/service/docs/erasure/troubleshooting-sessions.sql)
with the person's engineer email and Slack user id. Pass `''` for one that is not known. It runs as
one transaction:

```bash
psql "$DATABASE_URL" --single-transaction -v ON_ERROR_STOP=1 \
  -v email='alice@example.com' -v slack_id='U0123ABCD' \
  -f api/service/docs/erasure/troubleshooting-sessions.sql
```

It does three things:

1. Events the person wrote get `actor = 'erased'`, and their `text`, `reason` and `error` fields
   become `[erased]`. The events stay, so the log keeps its order and its sequence numbers.
2. In every other event, the person's email is replaced with `[erased]`, whatever its letter case.
3. In every other event, the person's Slack id is replaced with `erased` where it is a whole word,
   which covers mentions. A longer id that starts with the same characters is left alone.

`TroubleshootingErasurePostgresTest` runs this file against a session log and checks that neither
identifier is left in either table and that the log still reads in order.

To check by hand afterwards, both counts must be 0:

```sql
SELECT count(*) FROM troubleshooting_event e
WHERE row_to_json(e)::text ILIKE '%alice@example.com%' OR row_to_json(e)::text ~ '\mU0123ABCD\M';
SELECT count(*) FROM troubleshooting_session s
WHERE row_to_json(s)::text ILIKE '%alice@example.com%' OR row_to_json(s)::text ~ '\mU0123ABCD\M';
```

What it does not reach:

- Text that names the person without their email or Slack id, for example by first name. Slack
  messages from other people can do that. If the request needs it, search the `text` fields for
  the name and rewrite those events by hand, or delete the ticket's session (its events first).
- The Slack thread itself. Sessions copy it; erasing it in Slack is a separate step.
