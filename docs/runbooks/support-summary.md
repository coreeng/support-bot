# Runbook: Support Summary

The Support Summary page (`/summary`, [PR #327](https://github.com/coreeng/support-bot/pull/327))
classifies closed tickets with the LLM while serving the page and caches a prose summary per
window. Two operations need a human: rolling back past the `V38` migration, and re-running the
classification when a window will not clear on its own. Settings are in
[`api/service/docs/configuration.md`](../../api/service/docs/configuration.md#support-summary).

## Forward deployment

`V38` runs with the new pod's Flyway migrations at startup and marks the summary prompt as in use.
From that moment a pre-V38 pod cannot read the prompt table at all (see the rollback section for
why), so the old pod must be gone before the migration runs. The chart therefore sets
`strategy: Recreate` on the API Deployment (`helm-chart/templates/deployment.yaml`): Kubernetes
stops the old pod, then starts the new one. Expect a few seconds of downtime per deploy; there is
no overlap in which the old pod serves against migrated data.

If the new pod fails **after** migrating, the old one is not brought back automatically. Either
roll forward (fix the new version and deploy it again — the migration is already applied and is
idempotent), or, if you must go back to a pre-V38 release, run the rollback SQL below first.

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

## Re-running classification by hand

The page classifies closed tickets in the window that have no analysis for the in-use
classification prompt, then writes the summary. A thread whose LLM call fails is logged and
**skipped**, not retried: the summary is generated from what could be classified and the cached
snapshot is served as `ready` until the window's data changes (a ticket is raised or closes, is reclassified,
or the prompt changes). A transient LLM outage can therefore leave a window with a stuck
"Awaiting classification" count.

Note that the count also includes tickets that are still **open** — only closed tickets are ever
classified — so first check that the tickets in question are actually closed.

There is no button for this in the UI. A `SUPPORT_ENGINEER` (leadership alone is not enough)
can trigger the same job through the API:

```bash
# 202 Accepted: job started. 409 Conflict: a run is already in progress (see below).
# 400: days outside 1..365.
curl -i -X POST "$API_URL/analysis/run?days=14" -H "Authorization: Bearer $TOKEN"
```

`days` counts back from now over closed tickets, so pick a value that covers the window that is
stuck. Watch progress with:

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
below) polls and picks the new classifications up by itself. Threads that fail again are still
skipped — check the API logs for `Failed to analyze thread for ticket` if the count does not
move.

### Single-flight: the `analysis` lock

The manual run and the page's own refresh share one lock, the `async_job` row with id
`analysis`. Only one of them can run at a time, whichever window it is for:

- `POST /analysis/run` returns **409 Conflict** while a summary refresh (or another manual run)
  holds the lock. Wait for `GET /analysis/status` to report `running: false` and retry.
- While a manual run holds the lock, a summary page whose window needs a refresh shows
  `generating` and polls; once the lock is free its next poll starts that window's refresh. A
  window whose cached summary is still current is served as `ready` and is not polled.

A job that was interrupted by a restart is resumed at startup from the same row.

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
