# Enum codes: tags, impacts, teams

Tags, impacts and (escalation) teams are identified by an immutable `code` that acts as a
primary key. `label` is the display value and may be changed freely.

- **Do** change `label` to alter what users see.
- **Do not** change a `code` once in use — stored tickets/escalations reference it.
- **To remove** a value, delete it from config: it is soft-deleted, hidden from pickers, and
  still rendered (as "Retired") on existing records.
- **Renaming** a `code` = delete + add: the old code becomes orphaned. The bot now degrades
  gracefully (no crash) but historical references show the retired label, not the new one.

## Static teams

`platform-integration.teams-scraping.static.teams[]` accept an optional `code` (and `label`).
When omitted, `code` defaults to `name`, so the `name` doubles as the identity — renaming it
orphans references. Set an explicit `code` to keep identity stable while changing the display
`name`/`label`. The bot logs a startup warning for static teams without an explicit `code`.

## Startup validation

Codes must be unique and non-blank within each list — `enums.tags`, `enums.impacts`,
`enums.escalation-teams`, and the static `platform-integration.teams-scraping.static.teams`
(by effective code, i.e. `code` or, when omitted, `name`). The app validates this at startup and
**fails fast** with a clear message (`duplicate code 'X'` / blank code) before any data is written.

## Observability

A startup scan counts stored references to retired/removed codes and exposes them as the Micrometer
gauge `support_bot.orphaned_references{type=impact|tag|escalation_team}` (and logs them at ERROR).
It is fully guarded and non-fatal — it never blocks startup. See
[runbooks/orphaned-enum-references.md](runbooks/orphaned-enum-references.md).

Keep examples and codes generic — this repo is public.
