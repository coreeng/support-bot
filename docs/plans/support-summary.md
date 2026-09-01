# Support Summary page (EL-264)

Status: exploratory — high-level requirements only. No committed design yet; we
iterate together and update this doc as decisions land.

## Context

- Jira epic: EL-264 "Support Bot: Understand recent support demand and trends"
  (child stories EL-265, EL-266 — both Draft, treat as input material, not spec).
- Naming: the page is called **"Support Summary"** (per epic comment — not
  "Support Insights" as the prototype showed).
- There is an existing page, **Support Area Summary** at `/knowledge-gaps`.
  The new page follows a similar pattern. How it actually works (verified):

### How the existing page works (verified in code)

- Page: `ui/src/app/(dashboard)/knowledge-gaps/page.tsx` →
  `ui/src/components/knowledgegaps/knowledge-gaps.tsx` (~950 lines, everything
  inline). Sidebar entry in
  `ui/src/components/dashboard-layout/app-sidebar.tsx:50-55`.
- **Run Analysis** (`POST /analysis/run?days=N`, `AnalysisController`) is a
  per-ticket **classification** run, not a report generator:
  - Finds closed tickets in monitored channels with
    `last_interacted_at > today − days`, skipping tickets already analysed
    with the current prompt version
    (`JdbcThreadsAwaitingAnalysisRepository:46-76`).
  - For each, fetches + sanitises the Slack thread and sends it to the LLM
    with the in-use prompt from the `analysis_prompt` table
    (`LlmAnalysisService`). Provider is LangChain4j `ChatModel` — Vertex
    Gemini or a Basic-auth proxy (`LlmConfig`, PR #322).
  - Parses `Primary Driver` / `Category` / `Platform Feature` / `Reason` from
    the response and upserts **one row per ticket** into the `analysis` table
    (`driver`, `category`, `feature`, `summary`, `prompt_id`;
    `ON CONFLICT (ticket_id) DO UPDATE`).
  - Single-flight via an `async_job` DB lock; UI polls `/analysis/status`.
- **There is no snapshot and no prose report.** The two widgets (Top Support
  Areas, Top Knowledge Gaps) are top-5 aggregations computed **at read time**
  over the *entire* `analysis` table (`GET /summary-data/results`,
  `JdbcAnalysisRepository:43-134`). The `days` picker on the Run popover only
  scopes *which tickets get classified*, never what is displayed — the read
  side has no date filter at all.
- The "controlled taxonomy" lives in the prompt text itself, seeded in
  `V37__analysis_prompt.sql`.
- **Team data exists**: `ticket.team` (aka `authorsTeam` in the API/UI). No
  join from `analysis` to team today, but `analysis.ticket_id → ticket.team`
  makes a tenant leaderboard a straightforward join.
- Gating: read path (`/summary-data/results`, `/dashboard/**`) requires
  LEADERSHIP or SUPPORT_ENGINEER; `/analysis/run|status|prompt` require
  SUPPORT_ENGINEER (`SecurityConfig:62-75`). Frontend wraps pages in
  `RequireDashboardAccess` (`VIEW_RESTRICTED_DASHBOARDS` capability) plus a
  sidebar feature flag (`knowledge-gaps.enabled`).
- Date-range plumbing already exists elsewhere: `ui/src/lib/dateRange.ts` has
  a `last2Weeks` preset; pages like `health.tsx` / `stats.tsx` inline a
  Select + custom date inputs (no shared DateRangePicker component yet).
  Backend convention: `dateFrom`/`dateTo` `LocalDate` request params
  (`TenantInsightsController:25-27`).

## High-level requirements

1. **New page at `/summary`** ("Support Summary"), alongside the existing
   Support Area Summary — same overall pattern (on-demand analysis + saved
   snapshot + render).
2. **Time/period picker**, defaulting to the last 2 weeks. The existing page is
   window-less/fixed; this is the main structural difference.
3. **More data and widgets** than the existing page. From the epic, the page
   should answer four questions over the selected window:
   - Common issues and trends, and *why* tenants raise them (AI-generated
     summary + primary-driver split).
   - Subjects/categories ranked by frequency (against the controlled taxonomy).
   - Platform features with the most questions.
   - Top knowledge gaps: the knowledge-gaps page's widget (categories of
     `Knowledge Gap` tickets) scoped to the window, so both pages agree.
   - Top products, from product tags (`Product - <name>`), using the Products
     View's rules; hidden, like that tab, when no product tags are configured.
   - Tenant leaderboard (which teams raise the most issues). Requested
     extension (epic comment): per team also show issue count and the most
     frequent product they ask about — done as a sub-line under each team,
     from product tags.
4. **Open question:** which widgets are part of the LLM "Analysis" output vs.
   plain aggregations rendered directly from classifier data. Not decided —
   likely only the prose summary/trends needs the LLM; the ranked breakdowns
   can be straight aggregation.
5. Missing values are bucketed (Unclassified / None / Unknown), never dropped;
   counts should reconcile to the window total.
6. Access gating: same audience as the existing Support Bot pages.

## Decisions so far

- Name: "Support Summary", path `/summary`.
- Default window: 2 weeks, user-adjustable via period picker.
- Default window **ends yesterday** — "today" is treated as unfinished and
  excluded from the default view (keeps the window stable for the day →
  fewer LLM calls). Users can still include today explicitly (a "today"
  option or a custom range).
- **No "run" button.** Visiting the page loads data if available; if there
  are classification gaps in the window, the backfill starts automatically
  and the user waits with a count/progress bar (classification skips
  already-analysed tickets per prompt version, so most visits pay nothing;
  the `async_job` single-flight lock dedupes concurrent visitors).
- Backfill should be triggered server-side while serving page data, not by
  the user calling `/analysis/run` — avoids widening the SUPPORT_ENGINEER-only
  run permission to LEADERSHIP viewers.
- Breakdowns = live SQL over the window; prose summary = cached per
  (window, prompt version). (Agreed.)
- **Summary freshness = data fingerprint, no timers.** Since the visit
  classifies gaps anyway, regenerate the prose summary in the same wait
  whenever the window's data changed. Concretely: cache validity is a cheap
  fingerprint of the window's `analysis` rows (`count + max(updated_at)`)
  **plus its classification gaps** (count and id-sum of closed tickets with
  no analysis row for the current prompt); match → serve cached summary
  instantly, mismatch or no cache → regenerate. This also covers
  cross-window drift (a run triggered from one window classifying tickets
  that fall in another cached window) and late-closing tickets. Including
  the gaps matters for the failure case: a ticket the backfill can never
  classify (deleted Slack thread, persistently unparseable model output)
  would otherwise keep the window in a refresh loop on every poll; instead
  the snapshot is stored under a fingerprint that carries the gap and is
  served until the gap set changes. If summary generation fails,
  breakdowns still render; the summary section shows an error/retry.

## Backend decisions (agreed 2026-08-26)

1. **Window semantics**: ticket **created** time defines the window (matches
   the epic's "tickets raised in the last 14 days"). The gap/backfill query
   gets the same semantics.
2. **Gap-filling for arbitrary windows**: extend the existing
   awaiting-analysis query with `from`/`to` bounds (don't fork it).
3. **Open tickets**: keep classifying closed tickets only; expose an explicit
   "still open / not yet classified: N" bucket so window counts reconcile.
   Classifying open tickets = possible later scope, needs Arturs.
4. **Prompt changes**: only possible via Flyway migration (no write endpoint
   exists — `AnalysisPromptRepository` is read-only), so the "new prompt →
   everything reclassifies on first visit" cliff is a rare, deliberate
   release event. Accepted, no mitigation.
5. **Summary cache**: table `summary_snapshot(window_from, window_to,
   prompt_id, fingerprint, content, model, generated_at)`, unique on
   (window_from, window_to, prompt_id). Cache all ranges incl. custom; no
   eviction for MVP.
6. **Summary prompt**: second prompt kind via a `type` column on
   `analysis_prompt` (`classification` | `summary`). Input contract:
   aggregated counts per driver/category/feature/tenant plus the per-ticket
   `Reason` texts for the window. Iterate on content later.
7. **Orchestration**: `GET /summary?from&to` returns breakdowns immediately +
   summary state (`ready` | `generating` + progress | `unavailable`);
   backfill/generation triggered server-side; UI polls. Reuse the global
   `async_job` "analysis" lock — concurrent visitors share one run and see
   the same state.
8. **Feature flag**: whole page behind its own flag, which requires
   `analysis.prompt.enabled` — no degraded/partial mode.

## Open questions

- Relationship to the existing `/knowledge-gaps` page long-term (coexist?
  eventually merge?).
- Summary prompt content (seeded v1) — draft, then iterate against real data.

### `stub` LLM provider mode — ships or gets reverted?

**What it is.** A third provider mode alongside `vertex` and `proxy`, in
`AnalysisProps.Llm` / `LlmConfig`. `StubChatModel` implements LangChain4j's
`ChatModel` but makes no network call: it returns canned text that is a pure
function of the request. Classification requests get the strict
`Ticket` / `Primary Driver` / `Category` / `Platform Feature` / `Reason` block
the real parser expects, with values drawn from the seeded prompt's controlled
taxonomy and varied by a hash of the thread so the breakdowns spread across
buckets instead of collapsing into one bar. Summary requests get a short canned
paragraph that says it is stubbed.

It tells the two apart by the `--- BEGIN WINDOW REPORT ---` delimiter that
`LlmSummaryService` wraps its report in — a string owned by our code rather than
by prompt text stored in the database, since prompts get rewritten by migration
and delimiters do not. Anything without that marker is treated as a
classification request, which is the safer default: unparseable classification
output would silently skip every ticket, whereas an unrecognised summary only
produces odd prose. `StubChatModelTest` drives the real `LlmAnalysisService` and
`LlmSummaryService` rather than hand-built strings, so a change to either
delimiter fails the build instead of quietly degrading.

**Why it exists.** Both real modes need credentials a developer may not have:
`vertex` needs GCP IAM on the project, `proxy` needs the internal LLM proxy's
Basic token. The stub needs neither, and costs nothing, so the analysis run and
the Support Summary page can be exercised end to end — including the backfill,
the fingerprint cache and the progress states — on a laptop or in a demo.

**How to enable it** (local `application.yaml` only):

```yaml
analysis:
  llm:
    model-name: ${ANALYSIS_MODEL_NAME:stub-local}
    vertex:
      enabled: ${VERTEX_ENABLED:false}   # must be off: vertex defaults to true
    stub:
      enabled: ${STUB_LLM_ENABLED:false}
  prompt:
    enabled: ${ANALYSIS_PROMPT_ENABLED:true}
```

Exactly one of `vertex.enabled` / `proxy.enabled` / `stub.enabled` must be true,
validated fail-fast at startup (only when `analysis.prompt.enabled`). Because
`vertex.enabled` defaults to `true`, selecting this mode means turning vertex
off explicitly — the same step proxy mode has always needed. Set `model-name`
to something obviously fake: it is recorded verbatim in
`summary_snapshot.model`, so leaving it as `gemini-2.5-flash` would label
stubbed prose as if a real model had written it.

**Decision pending.** The risk is not the code — it reaches no network and holds
no credentials — but the data: stubbed classifications and summaries are written
to `analysis` and `summary_snapshot` exactly like real ones, and nothing in the
schema marks them as synthetic. Point a stub-enabled instance at a shared
database and the fake rows are indistinguishable from real ones. Either it ships
with that caveat understood (local-only, never against shared data), or it is
dropped before merge. It is deliberately confined to a **single commit**
(`feat(api): add stub LLM provider mode for local dev`), so reverting is
dropping that one commit — no untangling.
