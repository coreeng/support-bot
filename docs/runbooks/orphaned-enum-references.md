# Runbook: orphaned enum references

After a tag, impact, or (escalation) team `code` is renamed or removed from config, existing
tickets and escalations still reference the old code. The bot renders these gracefully — the
last-known label, marked "Retired" — and does **not** crash, but the stale references are worth
surfacing so operators can decide whether to re-link them.

## Signal

At startup the API scans the database for stored references to retired/removed codes and:

- logs them at **ERROR** (`Found stored references to retired/removed enum codes …`), and
- exposes a Micrometer gauge **`support_bot.orphaned_references{type=impact|tag|escalation_team}`**
  (also scrapeable on `/actuator/prometheus`).

The scan is fully guarded and **non-fatal** — it never blocks startup.

## When the gauge is > 0

1. **Expected?** Removing a value on purpose leaves its historical references orphaned; they keep
   rendering as "Retired". No action needed if this is intentional.
2. **Re-link them** by adding the original `code` back to config (the `label` may differ). The
   values un-retire on the next startup reconcile and existing records resolve to the active value
   again.
3. The gauge reflects the count at the last startup and refreshes on each restart.

## Notes

- Codes are immutable primary keys — prefer changing a `label` over renaming a `code`
  (see [../enum-codes.md](../enum-codes.md)).
- Duplicate or blank codes are a separate, **fatal** condition: the app fails fast at startup with
  a clear `duplicate code 'X'` / blank-code message.
