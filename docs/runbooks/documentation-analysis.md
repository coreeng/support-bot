# Runbook: Documentation Analysis

The Documentation Analysis page (`/documentation-analysis`) classifies a window's closed tickets
against the documentation with the LLM, in the background, while people use the page. Each ticket
is classified as `correct`, `missing`, `incorrect` (shown as "Conflicts with support's answer") or
`not_applicable`. The design is in [ADR-012](../adr/adr-012-documentation-gap-analysis.md); every
setting is in
[`api/service/docs/configuration.md`](../../api/service/docs/configuration.md#documentation-analysis).
What the page shows is in the user guides
([support engineer](../user-guides/role-support-engineer.md#documentation-analysis),
[leadership](../user-guides/role-leadership.md#documentation-analysis)).

## Enabling it

The feature is off by default. It needs all of these on the **API**:

1. An LLM provider: `LLM_PROVIDER` other than `none`
   (see [Analysis](../../api/service/docs/configuration.md#analysis-knowledge-gap-llm)).
2. `DOCUMENTATION_ANALYSIS_ENABLED=true`.
3. A documentation source. The only one is `github`, which is also the default of
   `DOCUMENTATION_ANALYSIS_SOURCE_TYPE`. Set:
   - `DOCUMENTATION_ANALYSIS_SOURCE_GITHUB_REPOSITORY` — `owner/name` of the documentation
     repository. Required.
   - `DOCUMENTATION_ANALYSIS_SOURCE_GITHUB_BRANCH` — default `main`.
   - `DOCUMENTATION_ANALYSIS_SOURCE_GITHUB_PATH` — the directory holding the documentation, default
     `docs`; empty or `.` for the repository root. Files with an extension in
     `documentation-analysis.source.github.file-extensions` (default `.md`, `.mdx`) are read.
4. GitHub credentials in `pr-review-tracking.github` (`GITHUB_TOKEN`, or the GitHub App settings),
   whether or not PR tracking is enabled. See [GitHub permission](#github-permission).

When it is enabled, startup fails with a message naming the setting if the source is unknown, the
repository or the GitHub credentials are missing, or a number or duration is not positive. With
the feature enabled, the sidebar shows **Documentation Analysis** under **Support Summary** to
leadership and support engineers. `GET /documentation-analysis/enabled` reports `true` only when
both the provider and the switch are on.

Migration `V47` seeds the documentation classification prompt (`analysis_prompt.type =
'documentation_classification'`, version 1) and marks it as in use. Nothing needs to be run by
hand.

There is no scheduled job and no button. Each visit to the page, and each poll it makes while a run
is active, may start a run over the window shown. Leave the feature off until the deployment is
ready to spend on it.

## GitHub permission

The feature needs **read access to the contents of the documentation repository, and nothing
more**. It makes two calls: it resolves the branch to its head commit, and downloads the repository
archive at that commit.

- **Fine-grained personal access token:** the documentation repository, with *Contents:
  Read-only*. GitHub adds *Metadata: Read-only* on its own.
- **GitHub App:** the same repository permission, *Contents: Read-only*, and the App installed on
  that repository.

The credentials are the ones PR tracking uses. If PR tracking is on, add the documentation
repository to what the token or App can read; it needs no other permission for this feature.

## Size, context and timeouts

The GitHub source sends the **whole documentation with every ticket**
(`documentation-analysis.source.github.context: whole`, the only value). Three limits follow:

- **`max-input-tokens`** (`DOCUMENTATION_ANALYSIS_MAX_INPUT_TOKENS`, default `150000`) is the
  bot's limit on one classification call: documentation, thread and prompt together. Sizes are
  estimated at 3.5 characters a token; this is an estimate, not the model's tokenizer.
  - When the documentation alone is over the limit, every run stops as `source_unavailable` with a
    reason that names `documentation-analysis.max-input-tokens` and the estimated size.
  - When one ticket's thread takes the call over the limit, that ticket fails with
    `context_too_large`.
- **The model's context window.** `max-input-tokens` has to be below it, with room for the answer
  and for the gap between the estimate and the real token count. A call the model refuses as too long
  fails like any provider call, so a `max-input-tokens` above what the model accepts shows up as
  `provider_error` failures and provider outages.
- **Provider timeouts.** A call that carries the documentation takes far longer than a Support
  Summary call. The defaults are likely too short:
  - `AI_PROXY_TIMEOUT` (proxy provider), default `20s`;
  - `ANTHROPIC_TIMEOUT` (Anthropic provider), default `60s`.

  Raise the one for your provider before enabling the feature. A timeout counts as a provider
  failure (see `provider_outage` under [Run states](#run-states)). These settings are shared with the Support
  Summary and the analysis run.

Changing `max-input-tokens`, the classification prompt, or a source setting that changes what it
returns (`source.type`, `context`, `repository`, `branch`, `path`, `file-extensions`) makes every
earlier result stale: it is hidden and its ticket is classified again on the next visit to its
window. A change to the documentation itself does not: a result is kept, with the version it was
made against, until one of those settings changes.

## Cost and rate limits

- **Daily limit.** `max-tickets-per-day` (`DOCUMENTATION_ANALYSIS_MAX_TICKETS_PER_DAY`, default
  `500`) caps the tickets attempted in any 24 hours, counted from the saved results and failed
  attempts. It is the cost limit: a run stops on reaching it, and no run starts until older
  attempts fall out of the 24 hours. A large window therefore fills in over several days. The limit
  counts tickets, not calls: a call retried after a provider error is one ticket.
- **Two LLM jobs at once.** A documentation run has its own lock (`async_job` row
  `documentation-analysis`) and its own worker, so it can call the provider while a Support Summary
  refresh or a manual `POST /analysis/run` is also calling it. Within each run calls stay one at a
  time, `LLM_REQUEST_DELAY` apart. Check the provider's limits for two callers at once, and above
  all its **input tokens per minute**: each documentation call is as large as the documentation.
- No cost or token figures have been measured for this feature yet. Watch the provider's usage
  after enabling it.

## Run states

The page shows the state of the run for its window. The state is held in memory, so a restart
clears it.

| State | What it means | What to do |
|---|---|---|
| `running` | A run is classifying this window. The page shows `done` of `total` (`total` is 0 until the run has counted the due tickets) and polls every 3 seconds. | Nothing. |
| `busy` | A run is active for another window, or on another instance. Only one runs at a time. The page shows that run's progress when this instance holds it, and polls; this window's run starts on a poll after the other ends. | Nothing. A `busy` state with no progress that never clears means an `async_job` row named `documentation-analysis` held by another instance; the chart runs one API replica. |
| `idle` | No ticket of the window is due: each has a current result, failed too recently, or is out of attempts. | Nothing. Check the [failed tickets](#failure-reason-codes) if the "Not yet classified" count stays above zero. |
| `source_unavailable` | The run could not open the documentation; the page shows the reason. No ticket is classified. | See [A failed fetch](#a-failed-fetch). |
| `provider_outage` | Five tickets in a row failed on the provider. The run stopped and no run starts before the time shown (`run-retry-delay`, default `15m`). | Check the provider's status, quota and the timeouts above. The five failures are not kept, so those tickets are tried again on the next run. |
| `daily_limit_reached` | The run stopped at `max-tickets-per-day`, or none could start because the limit is reached. | Wait, or raise the limit. |
| `failed` | The run ended on an unexpected error. The API logs `Documentation analysis for <from>..<to> failed` with the cause. | Fix the cause. The next visit starts a new run. |
| `no_prompt` | No documentation classification prompt is in use, so nothing can be classified. Every ticket counts as not yet classified. | Put a version in use (below). |

To put the documentation classification prompt back in use:

```sql
UPDATE analysis_prompt SET is_in_use = TRUE
WHERE type = 'documentation_classification'
  AND version = (SELECT max(version) FROM analysis_prompt WHERE type = 'documentation_classification');
```

A run interrupted by a restart keeps its `async_job` row and resumes at startup. Tickets already
saved are not classified again.

## Failure reason codes

A ticket that cannot be classified keeps a failed attempt in `doc_analysis` (`failure_reason`,
`failed_at`, `attempts`) and counts as failed on the page. The API logs
`Ticket <id> failed documentation classification: <code> (attempt <n>)`. A failed ticket is tried
again once `failure-retry-delay` (default `24h`) has passed, up to `max-attempts` (default `3`)
attempts; after that it waits until the prompt or a setting above changes.

```sql
SELECT failure_reason, count(*) FROM doc_analysis WHERE failed_at IS NOT NULL GROUP BY failure_reason;
```

| Code | What it means | What to do |
|---|---|---|
| `provider_error` | The provider call failed after three retries (waits of 5, 20 and 60 seconds): a rate limit, a timeout, a server or network error. | Check the timeouts and rate limits above. Five in a row stop the run as a provider outage instead. |
| `invalid_response` | The model answered, but the answer could not be read or broke the contract: a classification outside the four, no page or quote where one is required, no summary of support's answer for a conflict. | Occasional ones are expected and are retried. Many suggest the model is unsuited, or that its output is cut off (on Anthropic, check `ANTHROPIC_MAX_TOKENS`). |
| `quote_not_found` | A `correct` or `incorrect` answer quoted a passage that is not in the page it cites. | None; retried. This keeps unsupported verdicts off the page. |
| `unknown_page` | The answer named a page the documentation does not hold. | None; retried. |
| `no_support_answer` | The answer was `incorrect`, but the thread has no message from a support engineer to conflict with. | None; retried. |
| `undetermined` | The model said the documentation it was given cannot settle the question, or the thread is too unclear. | None; retried. It is not a fifth classification. |
| `context_too_large` | The thread, the prompt and the documentation together are over `max-input-tokens`. | Not retried until a setting changes. See [Size, context and timeouts](#size-context-and-timeouts). |
| `thread_unavailable` | The Slack thread could not be fetched. | Check the bot's Slack access to the channel. |
| `source_error` | The documentation source failed on this ticket. | Check the API log at the time of the failure. |

## A failed fetch

The source keeps a copy of the documentation under `copy-location` (default
`/tmp/documentation-analysis`) and fetches it again when a run starts and the copy is older than
`max-copy-age` (default `24h`). When the branch head has not moved, it only records the new fetch
time. The page shows the version (the commit, shortened) and when it was last refreshed. A new pod
starts with no copy, so its first run fetches one.

A failed fetch is logged as a warning, with the GitHub or archive error as its cause:

- **With an earlier copy held:**
  `Could not refresh the documentation from <owner/name>; using the copy of commit <sha> fetched at <time>`.
  The run goes on with the old copy, and the page keeps showing the old version and refresh time.
  It tries again on the next run.
- **With no copy held:** `Could not fetch the documentation from <owner/name>; no copy is held`.
  The run stops as `source_unavailable`, and the page shows *The documentation could not be fetched
  from GitHub, and no earlier copy is held.*

Common causes, in the log's cause:

- a 401, 403 or 404 from GitHub: the credentials cannot read the repository, or the repository or
  branch name is wrong (see [GitHub permission](#github-permission));
- `the documentation has more files than max-files allows (<n>)` or
  `the documentation is larger than max-copy-size`: raise `max-files` (default `5000`) or
  `max-copy-size` (default `50MB`), or narrow `path`;
- a write error under `copy-location`: the pod's `/tmp` is full or not writable.

The run also stops as `source_unavailable` when the copy is fetched but cannot be used:

- *The documentation holds no pages: no file under the configured path has a configured
  extension.* Check `path` and `file-extensions`.
- *The copy of the documentation could not be read.* The copy on disk is damaged. Delete the
  contents of `copy-location` and restart the API; the next run fetches a fresh copy.
- *The documentation is estimated at N tokens, more than documentation-analysis.max-input-tokens
  ...* See [Size, context and timeouts](#size-context-and-timeouts).

A file whose path holds a quote, an angle bracket or a control character is left out, with the
warning `Left out N documentation files whose paths hold a quote, angle bracket or control character`.
