# Runbook: Support Summary

The Support Summary page (`/summary`, [PR #327](https://github.com/coreeng/support-bot/pull/327))
classifies closed tickets with the LLM while serving the page and caches a prose summary per
window. Two operations need a human: rolling back past the `V38` migration, and re-running the
classification when a window will not clear on its own. Settings are in
[`api/service/docs/configuration.md`](../../api/service/docs/configuration.md#support-summary).

## Forward deployment

`V38` runs with the new pod's Flyway migrations at startup and marks the summary prompt as in use.
The API deploys as a single replica with the default rolling update, so for the few seconds
between the migration and the new pod becoming ready, the old pod is still serving and cannot
read the prompt table (see the rollback section for why). Fetching the prompt is rare, so this
window is accepted rather than paid for with downtime on every deploy; a later migration that
cannot tolerate an old reader should handle that on its own.

If the new pod fails **after** migrating, the old one keeps serving. Either roll forward (fix the
new version and deploy it again — the migration is already applied and is idempotent), or, if you
must go back to a pre-V38 release, run the rollback SQL below first.

## Rolling back to a release before V38

`V38__summary_snapshot_and_prompt_type.sql` adds `analysis_prompt.type` and seeds a second
in-use row (`type = 'summary'`). After it runs, **two** rows have `is_in_use = TRUE`: the
`classification` prompt and the `summary` prompt. The unique index that allows this is per type
(`analysis_prompt_in_use_idx ON analysis_prompt (type) WHERE is_in_use`).

A pre-V38 service does not know about `type`. It loads the prompt with
`WHERE is_in_use IS TRUE` and `fetchOne()`, so with two matching rows it throws
`TooManyRowsException`. The symptoms after a rollback are:

- `GET /analysis/prompt` returns 500, and
- every analysis run fails on `loadPrompt()`, so `POST /analysis/run` starts a job that errors
  immediately (`GET /analysis/status` reports the error).

The extra column, index and `summary_snapshot` table are harmless to the old service; only the
second in-use row breaks it. **Before** starting the old release, take the summary prompt out of
use:

```sql
UPDATE analysis_prompt SET is_in_use = FALSE WHERE type = 'summary';
```

Nothing else needs undoing. The classification prompt keeps its row, its version and its
`is_in_use` flag, so existing `analysis` rows stay valid.

### Rolling forward again

The migration is idempotent (`ON CONFLICT (type, version) DO NOTHING`), so it will **not** re-flag
the summary prompt. With no in-use summary prompt the page reports the summary as unavailable
(`No summary prompt version is marked as in use`), and `GET /summary/prompt` fails the same way.
Put the prompt back in use before or right after the new release starts:

```sql
UPDATE analysis_prompt SET is_in_use = TRUE
WHERE type = 'summary' AND version = (SELECT max(version) FROM analysis_prompt WHERE type = 'summary');
```

Check the result: exactly one row per type should be in use.

```sql
SELECT type, version FROM analysis_prompt WHERE is_in_use ORDER BY type;
```

Cached summaries key on the prompt version, so re-enabling the same version serves the existing
`summary_snapshot` rows without regenerating anything.

## Rolling back to a release before V41 (team groups)

`V41__summary_snapshot_team_group.sql` adds `summary_snapshot.team_group` (`''` for a summary of
the whole window, the group's code otherwise) and replaces the unique key
`summary_snapshot_window_prompt_unique (window_from, window_to, prompt_id)` with
`summary_snapshot_window_group_prompt_unique (window_from, window_to, team_group, prompt_id)`.

A pre-V41 service upserts with `ON CONFLICT (window_from, window_to, prompt_id)`, which has no
matching constraint any more, so every summary refresh fails; and once any team group's snapshot
exists for a window, its `find()` matches two rows and throws `TooManyRowsException`. **Before**
starting the old release, drop the group snapshots and restore the old key:

```sql
DELETE FROM summary_snapshot WHERE team_group <> '';
ALTER TABLE summary_snapshot DROP CONSTRAINT IF EXISTS summary_snapshot_window_group_prompt_unique;
ALTER TABLE summary_snapshot
    ADD CONSTRAINT summary_snapshot_window_prompt_unique UNIQUE (window_from, window_to, prompt_id);
```

The `team_group` column itself is harmless to the old service and can stay. Whole-window
snapshots keep their fingerprints, so they are still served. An in-flight
`{"type":"team-group-window",...}` row in `async_job` is unreadable to the old service, which
deletes it at startup and releases the lock — the next visit starts a fresh refresh.

### Rolling forward again

Flyway will not re-run V41, so put its key back by hand before or right after the new release
starts (otherwise the new service's group upserts fail):

```sql
ALTER TABLE summary_snapshot DROP CONSTRAINT IF EXISTS summary_snapshot_window_prompt_unique;
ALTER TABLE summary_snapshot
    ADD CONSTRAINT summary_snapshot_window_group_prompt_unique UNIQUE (window_from, window_to, team_group, prompt_id);
```

## Re-running classification by hand

The page classifies closed tickets in the window that have no analysis for the in-use
classification prompt, then writes the summary. A thread whose LLM call fails is logged and
**skipped** for that pass: the summary is generated from what could be classified and served as
`ready`. The gap is not forgotten, though — a cached snapshot that still has unclassified closed
tickets counts as stale once `SUMMARY_FAILURE_RETRY_DELAY` (default `15m`) has passed since it was
written, so the next visit after that retries the skipped tickets and regenerates the summary. Gaps
are retried at most once per retry delay, so a transient LLM outage clears itself on a later visit;
only a ticket that keeps failing stays in the "Awaiting classification" count. (The snapshot is also
regenerated sooner if the window's data changes — a ticket is raised or closes, is reclassified —
or the prompt changes.)

Note that the count also includes tickets that are still **open** — only closed tickets are ever
classified — so first check that the tickets in question are actually closed. Then check the API
logs for `Failed to analyze thread for ticket`: a ticket that fails on every retry needs the manual
run below, or a fix to whatever the log reports.

There is no button for this in the UI. A `SUPPORT_ENGINEER` (leadership alone is not enough)
can trigger the same job through the API:

```bash
# 202 Accepted: job started. 409 Conflict: a run is already in progress (see below).
# 400: days outside 1..365.
curl -i -X POST "$API_URL/analysis/run?days=21" -H "Authorization: Bearer $TOKEN"
```

`days` counts back from now over closed tickets, so pick a value that covers the window that is
stuck. The default **Last 2 Weeks** preset starts on the Monday two weeks back, up to 20 days ago
when viewed on a Sunday, so 21 covers it whichever day it is. Watch progress with:

```bash
curl -s "$API_URL/analysis/status" -H "Authorization: Bearer $TOKEN"
# {"jobId":"analysis","exportedCount":12,"analyzedCount":7,"running":true}
```

`running` flips to `false` when the job ends; `error` is present only if it failed. The run
persists each classification as it goes. It does **not** update the summary page on its own: a
page already showing a `ready` summary keeps it and does not poll, and the manual run does not
mark the summary as refreshing. Once `running` is `false`, **reload or revisit** the page: the
next `GET /summary` sees the changed data, regenerates the summary and shows `generating` until
it is done. Only a page that was already showing `generating` (because it hit the lock, see
below) polls and picks the new classifications up by itself. Threads that fail again are skipped
for that pass and retried after the next retry delay — check the API logs for
`Failed to analyze thread for ticket` if the count does not move.

## Failed refreshes

A refresh that fails (LLM error, prompt missing) is remembered **in process**: per window and
[team group](#team-groups), at most 64 entries, and only until `SUMMARY_FAILURE_RETRY_DELAY` passes or the window's data or prompt
changes. A restart forgets these failures, and each replica would keep its own set — the same
trade-off the in-memory `GET /analysis/status` already makes. That is acceptable with the chart's
single-replica API deployment (`helm-chart/templates/deployment.yaml` pins `replicas: 1`); if the
API is ever scaled out, the worst case is one extra retry per replica after a failure.

### Single-flight: the `analysis` lock

The manual run and the page's own refresh share one lock, the `async_job` row with id
`analysis`. Only one of them can run at a time, whichever window it is for:

- `POST /analysis/run` returns **409 Conflict** while a summary refresh (or another manual run)
  holds the lock. Wait for `GET /analysis/status` to report `running: false` and retry.
- While a manual run holds the lock, a summary page whose window needs a refresh shows
  `generating` and polls; once the lock is free its next poll starts that window's refresh. A
  window whose cached summary is still current is served as `ready` and is not polled.

A job that was interrupted by a restart is resumed at startup from the same row. A refresh for a
team group that no longer exists is resumed for the whole window instead
(logged as a warning) — the classification is still worth doing.

## Team groups

Team groups are built from a team attribute (`team-groups.attribute`, see
[configuration](../../api/service/docs/configuration.md#team-groups)): tenant teams with the same value
form one group. There is nothing to run by hand. Things worth knowing when a group's figures look
unexpected:

- **Startup log.** `Team groups from attribute '<name>': N group(s) over X of Y tenant team(s)` says
  how many teams have a value. It is a warning when none do, which usually means the attribute name
  is misspelled or the team provider doesn't set it. A team missing from every group has no value
  for the attribute, or one with no ASCII letters or digits (logged as a warning); the next line,
  `Tenant teams in no group`, names them.
- **Spellings.** Values that differ only in case, accents, or the spaces and punctuation between
  words share a group; the different spellings are logged at startup so they can be made consistent
  at the source.
- **Current teams only.** Groups are rebuilt at every startup from the teams fetched then. A team
  that is no longer fetched leaves its group, taking its past tickets out of the group's summary; a
  team that moves group takes its past tickets with it.
- **Renamed values.** A group's code comes from its value, so renaming the value changes the code:
  old links fall back to every team, and the group's narrative is regenerated under the new code.
- **Totals.** A group's totals are at most the whole window's: tickets with no team, or marked "Not
  a Tenant", belong to no group.
- **Membership changes** regenerate that group's narrative on its next visit; other groups and the
  whole window keep theirs. Membership only changes at a restart, when the teams are fetched again.
- **Classification follows the group.** Viewing a group classifies only the group's tickets.
  "All teams" classifies whatever in the window is still unclassified, including tickets no group
  shows (no team, "Not a Tenant", or a team in no group). Each ticket is classified once per
  classification prompt, so a ticket another view already classified is not redone. A ticket that
  cannot be classified (for example, its thread is gone) is retried only by the views that include
  it.

### Getting a bearer token

The API only accepts `Authorization: Bearer <jwt>` (the UI's Next.js routes forward the JWT held
in the NextAuth session; it is not exposed to the browser). To get one as a person:

- **Non-production with `security.test-bypass.enabled=true`:** no token is needed. Send
  `-H "X-Test-User: you@example.com" -H "X-Test-Role: support"` instead of the bearer header.
- **Production:** run the API's own login redirect and exchange the code yourself. This needs
  the API's callback (`$API_URL/login/oauth2/code/<provider>`) registered with the IdP — the
  optional step in the [SSO setup](../../api/service/docs/configuration.md#single-sign-on-sso);
  the UI's normal flow does not use it.
  1. Open `$API_URL/oauth2/authorization/<provider>` (`google`, `azure` or `dex`) in a browser
     and sign in.
  2. The API redirects to `<UI_ORIGIN>/login?code=<code>`. The UI does not exchange a bare
     `code` (its own flow adds a `provider` parameter), so the code stays unused while the page
     sits on its "Completing authentication..." spinner. Copy it from the address bar.
  3. Within **60 seconds** (the code is single-use and expires) exchange it:
     ```bash
     curl -s -X POST "$API_URL/auth/token" -H "Content-Type: application/json" \
       -d '{"code":"<code>"}'
     # {"token":"<jwt>"}
     ```
  The JWT is valid for 24 hours and carries the roles you hold in Slack, so it works only if you
  are in the support engineer group.
