# PR Tracking

Reference for the support-bot's PR review tracking feature: what it does, how it
is configured, and where the moving parts live in the codebase. Design rationale
lives in the ADRs ([adr-004][adr-004], [adr-006][adr-006], [adr-007][adr-007]);
this document is the operator-facing summary.

[adr-004]: ./adr/adr-004-pr-identification-and-sla-tracking.md
[adr-006]: ./adr/adr-006-repository-sla-discovery.md
[adr-007]: ./adr/adr-007-pr-lifecycle-tracking.md

---

## What it does

When a tenant posts a GitHub PR URL in a support thread, the bot:

1. **Detects** the PR, fetches its metadata, and starts tracking it.
2. **Replies in-thread** with an SLA acknowledgement (or a no-SLA acknowledgement).
3. **Polls GitHub** on a schedule for review state changes (approved, changes
   requested, merged, closed).
4. **Pauses / resumes the SLA clock** automatically when the PR is waiting on
   the author vs. waiting on review.
5. **Escalates** to the owning team if the SLA is breached.
6. **Auto-closes the ticket** once every tracked PR on the thread is resolved.

The whole pipeline is gated by a single feature flag and uses
`@ConditionalOnProperty` everywhere — when disabled, no beans, schedulers, or
controllers are instantiated.

---

## Tracked event types

Defined in `MessageEvent`
(`api/service/src/main/java/com/coreeng/supportbot/prtracking/MessageEvent.java`):

| Event               | When it fires                                         | Destination          |
|---------------------|-------------------------------------------------------|----------------------|
| `DETECTED`          | PR URL first observed in a Slack message              | Slack thread reply   |
| `APPROVED`          | Owning team posts an approval review                  | Slack thread reply   |
| `CHANGES_REQUESTED` | Owning team requests changes (pauses SLA)             | Slack thread reply   |
| `MERGED`            | PR is merged                                          | Slack thread reply   |
| `CLOSED`            | PR is closed without merging                          | Slack thread reply   |
| `ESCALATED`         | SLA deadline passes without approval                  | Slack thread + escalation card |

`ESCALATED` is only valid for repositories that have an SLA configured. It is
silently ignored for no-SLA repositories.

The persisted lifecycle states (`pr_tracking.status`) are `OPEN`,
`CHANGES_REQUESTED`, `APPROVED`, `ESCALATED`, `CLOSED`.

---

## Configuration

All config lives under `pr-review-tracking` in
`api/service/src/main/resources/application.yaml` (lines 106–155). The
properties record is `config/PrTrackingProps.java`.

### Global settings

```yaml
pr-review-tracking:
  enabled: true                            # master feature flag
  poll-cron: "0 0 9-18 * * 1-5"            # lifecycle poller schedule (9–18 UTC, Mon–Fri)
  pr-emoji: mag                            # Slack emoji added to the original message
  tags: PR                                 # tag code applied when bot auto-closes the ticket
  impact: Information Request              # impact code applied when bot auto-closes the ticket
  sla-discovery:
    cache: PT24H                           # how long to cache in-repo SLA files
  github:
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode:    ${GITHUB_AUTH_MODE:token}      # token | app
    token:        ${GITHUB_TOKEN:}               # PAT, when auth-mode=token
    app-id:       ${GITHUB_APP_ID:}              # GitHub App fields, when auth-mode=app
    installation-id:  ${GITHUB_APP_INSTALLATION_ID:}
    private-key-pem:  ${GITHUB_APP_PRIVATE_KEY_PEM:}
```

`tags` and `impact` must reference codes defined under `enums.tags` and
`enums.impacts`. `owning-team` (per repo) must reference a code under
`enums.escalation-teams`. Validation runs at startup and fails fast.

### Per-repository configuration

A repository entry has three flavours, distinguished by what is present:

**1. SLA-tracked repo** — SLA from a default duration:

```yaml
repositories:
  - name: my-org/my-repo
    owning-team: support-team
    github-team-slug: support-reviewers     # optional; falls back to PR's requested teams
    sla:
      default: 48h
      overrides:                            # optional, path-specific
        - path: infra/**
          sla: 7d
```

**2. SLA-tracked repo** — SLA discovered from a file in the repo
(see [adr-006][adr-006] for the discovery cache):

```yaml
  - name: my-org/my-repo
    owning-team: support-team
    sla:
      file: .pr-sla.yaml
      default: 48h                          # fallback when the file is missing or invalid
```

**3. No-SLA repo** — track only PRs touching specific paths, with no
escalation:

```yaml
  - name: my-org/no-sla-repo
    owning-team: support-team
    paths:
      - infra/**
      - rbac/**
```

Validation rules (`PrTrackingProps`, lines 60–78):
- At least one repository when `enabled: true`.
- Repository names unique and in `org/repo` form.
- `owning-team` must exist in `enums.escalation-teams`.
- A repo configures **either** an `sla` block **or** `paths`, not both.
- No-SLA repos must have a non-empty `paths` list.

### Custom event messages

Each repo can override the default Slack message for any event using a CEL
expression (`PrMessageRenderer.java`). Templates are compiled at startup;
compilation failures log a warning and fall back to the built-in default — the
feature is fail-safe.

```yaml
    messages:
      detected:           '"PR " + string(pr_number) + " detected. SLA: " + sla_duration + ", deadline: " + sla_deadline + "."'
      escalated:          '"Contact #pr-reviews in Slack to chase this review."'
      approved:           '"PR " + string(pr_number) + " approved — ready to merge!"'
      changes-requested:  '"Changes requested on PR " + string(pr_number) + ". Please review the feedback."'
      merged:             '"PR " + string(pr_number) + " merged. Thanks!"'
      closed:             '"PR " + string(pr_number) + " closed."'
```

Available CEL variables:

| Variable        | Type   | Notes                                                       |
|-----------------|--------|-------------------------------------------------------------|
| `pr_number`     | int    | Convert with `string(pr_number)` for concatenation          |
| `pr_url`        | string | Full GitHub PR URL                                          |
| `repo_name`     | string | `org/repo`                                                  |
| `repo_url`      | string | Full GitHub repo URL                                        |
| `owning_team`   | string | Team code from `enums.escalation-teams`                     |
| `sla_duration`  | string | e.g. `2 days`; empty for no-SLA repos                       |
| `sla_deadline`  | string | e.g. `Wed 08 May at 17:00 UTC`; empty for no-SLA repos      |

Setting `escalated` on a no-SLA repo is rejected at startup
(`PrTrackingProps.Messages`, lines 255–260).

---

## Persistence

Table `pr_tracking`, defined across migrations `V11__pr_tracking.sql`,
`V13__pr_tracking_lifecycle_columns.sql`, and `V15__pr_tracking_has_sla.sql`.

Key columns:

| Column                     | Purpose                                                           |
|----------------------------|-------------------------------------------------------------------|
| `ticket_id`                | FK to the support ticket that surfaced the PR                     |
| `github_repo`, `pr_number` | Identify the PR                                                   |
| `status`                   | `OPEN` / `CHANGES_REQUESTED` / `APPROVED` / `ESCALATED` / `CLOSED`|
| `sla_deadline`             | Set while the SLA clock is running                                |
| `sla_remaining`            | Set while the SLA clock is paused (waiting on author)             |
| `escalation_id`            | FK to the escalation record once breached                         |
| `last_review_at`           | Latest actionable review timestamp                                |
| `last_author_activity_at`  | Used to decide when to resume a paused SLA                        |
| `has_sla`                  | Per-row flag — whether this record was created with an SLA        |
| `closed_at`                | Set when status moves to `CLOSED`                                 |

Constraints worth knowing:
- `UNIQUE(ticket_id, github_repo, pr_number)` — one tracking record per
  ticket+PR.
- `CHECK` enforces that `sla_deadline` and `sla_remaining` are mutually
  exclusive (V13).

Repository operations are in `PrTrackingRepository.java` /
`JdbcPrTrackingRepository.java`: `insertIfAbsent`, `updateStatus`, `pauseSla`,
`resumeSla`, `findAllActive`, `findAllInFlight`, `getInsightsByRepo`,
`getEscalationBreakdown`.

---

## REST API

All endpoints are conditional on `pr-review-tracking.enabled: true`.

### Production endpoints

`api/service/src/main/java/com/coreeng/supportbot/prtracking/rest/`

| Method | Path                                       | Returns               | Notes                                              |
|--------|--------------------------------------------|-----------------------|----------------------------------------------------|
| GET    | `/tenant-insights/enabled`                 | `{ "enabled": bool }` | Feature-flag probe used by the UI                  |
| GET    | `/tenant-insights/pr-stats`                | `RepoInsights[]`      | Optional `dateFrom`, `dateTo` (ISO-8601 dates)     |
| GET    | `/tenant-insights/escalation-breakdown`    | `EscalationBreakdown` | Optional `dateFrom`, `dateTo`                      |
| GET    | `/tenant-insights/in-flight-prs`           | `InFlightPr[]`        | Optional `team` filter                             |

Response shapes are defined in `RepoInsights.java`, `EscalationBreakdown.java`,
and `InFlightPr.java` (same package). The TypeScript mirrors live in
`ui/src/lib/types/dashboard.ts`.

### Test-only endpoints

Active under Spring profile `functionaltests` or `nft`
(`PrTrackingTestController.java`). Used by functional tests to drive the poller
deterministically and seed/clean records — never expose in production.

---

## UI

PR tracking surfaces in the **Tenant Requests** dashboard
(`ui/src/components/tenant-requests/`):

- **Stats tab** (`tenant-requests.tsx`) — table of `RepoInsights` per repo:
  PR count, open, escalated, breached, p50/p90/p99 time-to-resolution; date
  range filter; intervention rate card derived from `EscalationBreakdown`.
- **In-flight tab** (`in-flight-prs.tsx`) — every active tracking record with
  per-row SLA badge (active / paused / breached / no-SLA / unknown), age,
  waiting-on, deep-link to the Slack thread; team filter and search.

Data fetching uses React Query hooks in `ui/src/lib/hooks/index.ts`
(`useTenantInsightsEnabled`, `useTenantInsightsStats`,
`useEscalationBreakdown`, `useInFlightPrs`); requests are proxied through
Next.js routes under `ui/src/app/api/tenant-insights/`. The dashboard hides
itself when `/tenant-insights/enabled` returns `false`.

---

## Runtime flow

### Detection (`PrDetectionService`)

Triggered on every Slack message posted in a support thread:

1. Parse PR URLs (`GitHubPrUrlParser`).
2. Fetch PR metadata from GitHub; skip if already merged/closed.
3. Skip if a tracking row already exists for `(ticket, repo, pr)`.
4. Resolve SLA: per-path overrides → in-repo file → repo default; or fall
   through to no-SLA path-matching.
5. Insert the tracking row, react with `pr-emoji` on the original message,
   and post the `DETECTED` message in-thread.
6. Multiple PRs from the same repo in one message are batched into a single
   reply unless custom messages are configured.

### Lifecycle polling (`PrLifecyclePoller`)

Runs on `poll-cron`. For each active record:

1. Fetch latest reviews; filter to the owning team
   (`TeamReviewFilter` — by `github-team-slug`, falling back to PR's requested
   teams).
2. Determine the latest *actionable* review (most recent approve / changes
   requested, ignoring dismissed).
3. Apply transitions:
   - `OPEN → CHANGES_REQUESTED` → pause SLA (store remaining duration).
   - `CHANGES_REQUESTED → OPEN` (after author activity) → resume SLA
     (compute new deadline).
   - `* → APPROVED` on approval review.
   - `APPROVED/OPEN → ESCALATED` when SLA deadline passes — creates an
     escalation via `EscalationProcessingService` and links
     `escalation_id`.
   - `* → CLOSED` when the PR is merged or closed; clears SLA fields, sets
     `closed_at`.
4. Post the corresponding event message in-thread.
5. Once every tracked PR on the ticket is `CLOSED`, the bot auto-closes the
   ticket with the configured `tags` and `impact`.

---

## Code map

| Concern                  | Location                                                                                          |
|--------------------------|---------------------------------------------------------------------------------------------------|
| Detection                | `api/service/src/main/java/com/coreeng/supportbot/prtracking/PrDetectionService.java`             |
| Lifecycle poller         | `api/service/src/main/java/com/coreeng/supportbot/prtracking/PrLifecyclePoller.java`              |
| Message templating       | `api/service/src/main/java/com/coreeng/supportbot/prtracking/PrMessageRenderer.java`              |
| Event enum               | `api/service/src/main/java/com/coreeng/supportbot/prtracking/MessageEvent.java`                   |
| SLA resolution           | `api/service/src/main/java/com/coreeng/supportbot/prtracking/SlaLookup.java`                      |
| Review filtering         | `api/service/src/main/java/com/coreeng/supportbot/prtracking/TeamReviewFilter.java`               |
| Persistence              | `api/service/src/main/java/com/coreeng/supportbot/prtracking/{PrTrackingRecord,PrTrackingRepository,JdbcPrTrackingRepository}.java` |
| REST controllers         | `api/service/src/main/java/com/coreeng/supportbot/prtracking/rest/`                               |
| Configuration properties | `api/service/src/main/java/com/coreeng/supportbot/config/PrTrackingProps.java`                    |
| YAML defaults            | `api/service/src/main/resources/application.yaml` (lines 106–155)                                 |
| Migrations               | `api/service/src/main/resources/db/migration/V{11,13,15}__pr_tracking*.sql`                       |
| UI components            | `ui/src/components/tenant-requests/`                                                              |
| UI types                 | `ui/src/lib/types/dashboard.ts`                                                                   |
| UI data hooks            | `ui/src/lib/hooks/index.ts`                                                                       |
| Functional tests         | `api/functional/src/test/java/com/coreeng/supportbot/PrTrackingFunctionalTests.java`              |
