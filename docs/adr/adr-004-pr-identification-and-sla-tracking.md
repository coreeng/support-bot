# ADR: PR Identification and SLA Tracking in Support Threads

**Date:** 2026-02-25
**Status:** Accepted

---

## Context

A common category of support request we come across is a PR review request: a team posts a PR link in the support channel asking for review.
The support bot currently has no awareness of PR links — no SLA enforcement, no automated escalation, and no lifecycle tracking.
Support engineers must manually manage these requests, requiring frequent context switches that lead to inconsistent support experiences.

This ADR describes how to give the support bot first-class awareness of PR review requests: detect them, communicate the applicable SLA,
and automatically escalate or close the thread as the PR progresses. The capability is additive and off by default.

---

## Affected Personas

- **Tenant teams**: post PR review requests in support threads; receive automated SLA acknowledgement and status updates.
- **Owning / platform teams**: receive escalation mentions only after SLA expiry.
- **Support engineers**: reduced manual tracking overhead; PR lifecycle visible directly in the thread.

---

## Decision Drivers

- Do not affect existing support thread flows when disabled.
- Keep configuration close to the deployment (Helm chart) — environment-specific behaviour without code changes.
- Escalation only after SLA expiry, to reduce noise.

---

## Decision

### 1. Feature Flag

A single property gates the entire pipeline, defaulting to `false`. All related beans, scheduled tasks, and event handlers
use `@ConditionalOnProperty` so nothing is instantiated when off.

```yaml
pr-review-tracking:
  enabled: ${PR_REVIEW_TRACKING_ENABLED:false}
```

`PR_REVIEW_TRACKING_ENABLED` can be set per environment via deployment configuration (for example Helm/env vars) to toggle the feature.

### 2. Repository Configuration

Tracked repositories are defined in Spring config:

```yaml
pr-review-tracking:
  repositories:
    - name: my-org/onboarding-repo
      owning-team: wow
      sla: PT48H
    - name: my-org/another-repo
      owning-team: infra-integration
      sla: PT72H
```

- `name` — matched against the `org/repo` component of GitHub PR URLs.
- `owning-team` — escalation team **code** (from `enums.escalation-teams[*].code`).
- Team `label` (for bot messages) and `slack-group-id` (for tagging) are resolved from `enums.escalation-teams` using `owning-team`.
- `sla` — ISO-8601 duration (e.g. `PT48H`, `PT72H`), parsed to `java.time.Duration`.

### Behaviour Configuration

Additional PR-tracking behaviour is configured via:

```yaml
pr-review-tracking:
  pr-emoji: pr
  tags: [networking]
  impact: productionBlocking
```

- `pr-emoji` — Slack emoji name added to the query message when an **open** PR is tracked.
- `tags` — tag codes used as defaults during top-level metadata initialisation when ticket tags are empty, and passed to the PR-resolution close flow.
- `impact` — impact code used as a default during top-level metadata initialisation when ticket impact is unset, and passed to the PR-resolution close flow.

### 3. GitHub Credentials

The bot supports two auth modes for GitHub API access:

- `token` — use a configured bearer token directly (typically PAT).
- `app` — mint GitHub App installation tokens in-process in the API (JWT + installation token exchange), with refresh before expiry.

```yaml
pr-review-tracking:
  github:
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode: ${GITHUB_AUTH_MODE:token} # token | app
    token: ${GITHUB_TOKEN:} # Used only when auth-mode=token (typically PAT)
    app-id: ${GITHUB_APP_ID:}
    installation-id: ${GITHUB_APP_INSTALLATION_ID:}
    private-key-pem: ${GITHUB_APP_PRIVATE_KEY_PEM:}
```

The `api-base-url` override supports GitHub Enterprise.

Startup validation enforces required fields per mode (`token` requires `token`; `app` requires `app-id`, `installation-id`, `private-key-pem`).

### 4. PR Link Detection

The existing Slack event listener is extended to match GitHub PR URLs (`github.com/<org>/<repo>/pull/<number>`) via compiled regex.
If `<org>/<repo>` matches a configured repository, the PR is in-scope. Multiple links per message are each tracked independently.
Unrecognised repos are silently ignored.

### 5. On PR Detection

When an in-scope PR link is found:

1. Fetch PR metadata via GitHub API (`created_at`, current state).
2. For PR links detected on the top-level query message (not thread replies), if the ticket is missing metadata, initialise it with configured defaults (`tags`, `impact`) and suggested author team when available. Existing team/tags/impact are preserved.
3. Post a thread reply with SLA tracking messaging. If the SLA is still within bounds, the message states the deadline and owning team label (named, not tagged). If already breached at detection time, the message states that the timeframe has been exceeded and the ticket is escalated immediately to the configured owning team.
4. React to the **top-level thread message** with the configured PR emoji (`pr-emoji`, default `pr`) to indicate the ticket contains a tracked PR request, regardless of which reply introduced the PR link.
5. Persist a `pr_tracking` record for lifecycle polling, including whether ticket auto-close is allowed for this PR (`close_ticket_on_resolve`: true for top-level detections, false for thread-reply detections).

### 6. PR Tracking State

The API persists PR tracking records linked to the existing ticket model:

```sql
create table if not exists pr_tracking
(
    id                      bigserial,
    ticket_id               bigint,
    github_repo             text,
    pr_number               integer,
    pr_created_at           timestamptz,
    sla_deadline            timestamptz,
    owning_team             text,
    status                  pr_tracking_status, -- OPEN | ESCALATED | CLOSED
    escalation_id           bigint,        -- set when auto-escalation is created
    close_ticket_on_resolve boolean not null default true,
    closed_at               timestamptz,
    created_at              timestamptz not null default now()
);
```

### 7. Periodic Lifecycle Polling

A `@Scheduled` task runs on a business-hours cron (default: `0 0 9-18 * * 1-5`, configurable via `pr-review-tracking.poll-cron`) and processes records where `status IN ('OPEN', 'ESCALATED')`:

- **PR merged or closed** — set `status = CLOSED`, `closed_at`. Post a closure message in the thread and close the support thread only when no active **closable** tracked PRs remain (thread reply-origin PR tracking does not trigger auto-close).
- **PR open, SLA expired, not yet escalated** — create escalation, set `status = ESCALATED`, persist `escalation_id`, and post an escalation message tagging the owning team (resolved from escalation-team config).
- **PR open, within SLA** — no action.

**Note on "responded to" vs "closed":** In v1, escalation triggers purely on SLA expiry + PR still open. This means a PR
where the owning team has already responded (e.g., requested changes) but the PR author hasn't acted will still be
escalated — a false positive. GitHub's Reviews API (`GET /repos/{owner}/{repo}/pulls/{number}/reviews`) exposes review
activity (`APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`) which could be checked before escalating to suppress these cases.
This is deferred to the PR Lifecycle Tracking follow-up to keep v1 scope contained, but should be prioritised if false escalations prove disruptive.

When a thread contains multiple tracked PRs, the thread is only auto-closed once all active PRs that allow auto-close are resolved.

---

## Consequences

### Positive

- Automated SLA enforcement without manual monitoring.
- Owning teams only tagged after SLA expiry — low noise.
- Controlled rollout via Helm values; off by default.
- Durable state survives pod restarts; polling resumes correctly.
- Additive — no impact on existing thread handling when disabled.

### Negative / Trade-offs

- **GitHub API rate limits** — one call per open PR per poll cycle. Poll cadence is configurable via `pr-review-tracking.poll-cron`; adaptive backoff is a follow-up.
- **No real-time close detection** — closure detected at next poll interval (minutes, not seconds). Acceptable for hour/day-scale SLAs; GitHub webhooks could address this later.
- **Thread auto-close on PR close** — if a thread has non-PR topics, auto-closing may be premature. Making this opt-in per repo config is a follow-up.
- **No review-awareness in v1** — escalation is based solely on SLA expiry + PR still open. If the owning team has already reviewed (e.g., requested changes) but the PR author hasn't acted, the bot will still escalate — a false positive. Incorporating GitHub review state is deferred.

### Neutral

- `pr_tracking` records are retained after closure for potential future reporting.
- No changes to existing Slack listeners beyond the additional PR URL check.
