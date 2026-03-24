# ADR: PR Lifecycle Tracking

**Date:** 2026-03-24
**Status:** Proposed

---

## Context

[ADR-004](adr-004-pr-identification-and-sla-tracking.md) introduced PR detection and SLA tracking. SLA deadlines are calculated from the PR creation date, and escalation triggers purely on SLA expiry with the PR still open. The system has no awareness of review activity.

This creates problems:

- **False escalations** — if the owning team has already reviewed and requested changes, the bot still escalates when the SLA expires, even though the ball is in the tenant's court.
- **No visibility into PR review state** — support engineers and dashboards can't distinguish between "waiting for review" and "waiting for tenant to address feedback."
- **SLA runs regardless of context** — the clock ticks even when the tenant is the blocker, making SLA metrics unreliable.
- **Tickets stay open after approval** — a PR approved and ready to merge still shows as an open support ticket until someone actually clicks merge.

ADR-004 explicitly deferred review-awareness to this follow-up. This ADR describes how to incorporate GitHub review state into the PR lifecycle, pause/resume SLA tracking based on whose turn it is, and close tickets earlier when PRs are approved and mergeable.

---

## Affected Personas

- **Tenant teams**: receive clearer status updates reflecting actual PR state; tickets close sooner when PRs are approved.
- **Owning / platform teams**: no longer falsely escalated when they've already reviewed; escalation only triggers when the review ball is genuinely in their court.
- **Support engineers**: PR lifecycle state visible in threads and dashboards; reduced noise from false escalations.

---

## Decision Drivers

- Reduce false escalations by making the bot aware of whose turn it is.
- SLA should only count time when the owning team is the blocker.
- Tickets should close as early as practically possible (approval + mergeable, or merged).
- Dashboard visibility into PR state per team.
- Backward-compatible — existing deployments with v1 behaviour should continue working without config changes.

---

## Decision

### 1. GitHub Review State Awareness

The bot will fetch review activity from the GitHub Reviews API (`GET /repos/{owner}/{repo}/pulls/{number}/reviews`) during each poll cycle, in addition to the existing PR state check. This provides:

- `APPROVED` — a reviewer has approved the PR.
- `CHANGES_REQUESTED` — a reviewer has requested changes.
- `COMMENTED` — a reviewer has left a comment (informational, not blocking).

The bot will also check the PR's `mergeable` state via the GitHub API to determine whether the merge button is actionable (all required reviews approved, CI passing, no merge conflicts).

### 2. Extended PR Tracking States

The `pr_tracking_status` enum is extended from `OPEN | ESCALATED | CLOSED` to:

| Status | Meaning | SLA Clock |
|--------|---------|-----------|
| `OPEN` | PR open, awaiting review from owning team | Running |
| `CHANGES_REQUESTED` | Owning team requested changes, awaiting tenant action | **Paused** |
| `APPROVED` | Owning team approved, awaiting merge | **Paused** |
| `ESCALATED` | SLA breached while awaiting owning team review | Expired |
| `CLOSED` | PR merged, closed, or approved + mergeable | Stopped |

State transitions:

```
OPEN ──────────────────────────────→ ESCALATED (SLA expires while OPEN)
  │                                       │
  ├─→ CHANGES_REQUESTED ───────────────→  │ (SLA expires after resuming)
  │       │                               │
  │       ├─→ OPEN (tenant pushes) ─────→ │
  │       │                               │
  │       └─→ APPROVED ─→ CLOSED          │ (owning team approves directly)
  │                        (mergeable)    │
  │                                       │
  ├─→ APPROVED ───→ CLOSED (mergeable)    ├─→ APPROVED ──→ CLOSED (mergeable)
  │                                       │
  └─→ CLOSED (merged/closed)              └─→ CLOSED (merged/closed)
```

Any state can transition to `CLOSED` if the PR is merged or closed on GitHub.

### 3. SLA Clock Pausing and Resuming

The SLA deadline is currently stored as an absolute timestamp (`sla_deadline`). To support pausing, we introduce:

- `sla_remaining` — the remaining SLA duration at the point the clock was paused (stored as an interval).
- When the clock **pauses** (transition to `CHANGES_REQUESTED` or `APPROVED`): `sla_remaining = sla_deadline - now()`. The `sla_deadline` is set to `NULL` to indicate the clock is paused.
- When the clock **resumes** (transition back to `OPEN`): `sla_deadline = now() + sla_remaining`. The `sla_remaining` is set to `NULL`.
- Escalation only triggers when `status = OPEN` and `now() > sla_deadline` (unchanged logic, but now only fires when the owning team is genuinely the blocker).

**Open question for team review:** Should the SLA clock resume with remaining time, or reset to a fresh duration? This ADR proposes resuming remaining time, but this should be confirmed during review.

### 4. Approval Validation

Not all approvals are meaningful for closing a support ticket. The bot must verify that the approval comes from the owning team:

- On each poll cycle, fetch the list of reviews for the PR.
- An approval is considered valid if the approving user is a member of the configured `owning-team`'s GitHub team or is listed as a required reviewer / CODEOWNER for the changed files.
- Approvals from the PR author's own team or unrelated users are ignored for the purpose of ticket closure.

**Implementation note:** Validating team membership requires the GitHub Teams API (`GET /orgs/{org}/teams/{team}/members`) or checking the PR's requested reviewers against the configured team. The specific mechanism will be determined during implementation based on GitHub API access and permissions available.

### 5. Ticket Closure Conditions

A support ticket is closed when **all** active closable PR tracking records for that ticket reach `CLOSED` status. A PR tracking record transitions to `CLOSED` when any of the following occurs:

| Condition | Slack Message |
|-----------|---------------|
| PR is merged | "PR `repo#123` has been merged. :white_check_mark:" (existing) |
| PR is closed (not merged) | "PR `repo#123` has been closed. :white_check_mark:" (existing) |
| PR is approved by owning team AND mergeable | "PR `repo#123` has been approved and is ready to merge. :white_check_mark:" |

"Mergeable" means the GitHub API reports the PR as mergeable (`mergeable = true`, checks passing, no blocking reviews outstanding). If the PR is approved but not yet mergeable (e.g., CI still running, merge conflicts), the bot does not close — it remains in `APPROVED` state and will be checked again on the next poll cycle.

### 6. Polling Enhancements

The existing `PrLifecyclePoller` is extended to handle the new states. For each active record (`OPEN`, `CHANGES_REQUESTED`, `APPROVED`, `ESCALATED`):

1. Fetch PR state, reviews, and mergeable status from GitHub.
2. If **merged or closed** → transition to `CLOSED`, post message, check ticket auto-close. This takes priority regardless of current tracking status.
3. If **approved by owning team**:
   - If **mergeable** → transition to `CLOSED`, post approval message, check ticket auto-close. Applies from any non-CLOSED status, including `ESCALATED` (owning team responded after escalation).
   - If **not mergeable** → transition to `APPROVED`, pause SLA clock (if not already paused/expired).
4. If **changes requested by owning team** and current status is `OPEN` or `ESCALATED` → transition to `CHANGES_REQUESTED`, pause SLA clock, post message: "PR `repo#123`: changes have been requested by the reviewing team. SLA clock paused." For `ESCALATED` records, the SLA is already expired so pausing has no practical effect, but the status change provides accurate visibility.
5. If status is `CHANGES_REQUESTED` and **new commits pushed since the last `CHANGES_REQUESTED` review** → transition back to `OPEN`, resume SLA clock, post message: "PR `repo#123`: changes have been addressed. SLA clock resumed." This is determined by comparing the timestamp of the most recent commit on the PR head against the `submitted_at` timestamp of the last `CHANGES_REQUESTED` review.
6. If status is `OPEN` and **SLA deadline passed** → escalate (existing behaviour).

### 7. Activity Tracking

To support "last time the team interacted with a PR" and dashboard reporting, we track key activity timestamps:

- `last_review_at` — timestamp of the most recent review from the owning team (approval, changes requested, or comment).
- `last_author_activity_at` — timestamp of the most recent push or comment from the PR author.

These are updated on each poll cycle from GitHub API data and stored on the `pr_tracking` record.

### 8. Schema Changes

```sql
-- Extend the status enum
ALTER TYPE pr_tracking_status ADD VALUE 'CHANGES_REQUESTED';
ALTER TYPE pr_tracking_status ADD VALUE 'APPROVED';

-- Add new columns to pr_tracking
ALTER TABLE pr_tracking ADD COLUMN sla_remaining interval;
ALTER TABLE pr_tracking ADD COLUMN last_review_at timestamptz;
ALTER TABLE pr_tracking ADD COLUMN last_author_activity_at timestamptz;
```

When `sla_deadline` is `NULL` and `sla_remaining` is not `NULL`, the SLA clock is paused. When `sla_deadline` is not `NULL` and `sla_remaining` is `NULL`, the SLA clock is running.

### 9. Dashboard

A new tab in the support UI (Analytics & Operations) will display pending PR tracking records per team. Details of layout and filtering will be defined separately, but the API will expose the data needed:

- PR link, repository, PR number
- Current tracking status (`OPEN`, `CHANGES_REQUESTED`, `APPROVED`, `ESCALATED`)
- SLA deadline or remaining time (if paused)
- Last reviewer activity timestamp
- Last author activity timestamp
- Owning team
- Associated ticket

### 10. Multi-Team Reviews

Some PRs may require reviews from multiple teams (e.g., CODEOWNERS assigns both the owning team and the tenant's team as required reviewers). This ADR acknowledges the scenario but defers detailed design. The current model tracks one `owning_team` per PR tracking record. Supporting multi-team review tracking may require multiple tracking records per PR or a separate review-tracking model, to be designed as a follow-up.

---

## Consequences

### Positive

- Eliminates false escalations when the owning team has already reviewed.
- SLA metrics become meaningful — they measure time the owning team is the blocker, not total PR age.
- Tickets close earlier when PRs are approved and mergeable, reducing open ticket noise.
- Dashboard gives support engineers and team leads visibility into PR state across teams.
- Backward-compatible — existing `OPEN → ESCALATED → CLOSED` flow is preserved; new states are additive.

### Negative / Trade-offs

- **Increased GitHub API usage** — each poll cycle now fetches reviews and mergeable status in addition to PR state. Rate limit impact should be monitored; batching or conditional fetching (e.g., only fetch reviews for OPEN records near SLA deadline) can mitigate this.
- **Team membership validation complexity** — checking whether an approver belongs to the owning team requires additional API calls or a cached team membership list. The implementation approach depends on available GitHub permissions.
- **SLA pause/resume adds complexity** — the `sla_deadline` / `sla_remaining` swap requires careful handling to avoid clock drift or missed escalations. Edge cases (e.g., multiple rapid state changes) need thorough testing.
- **No real-time state detection** — review state changes are detected at next poll interval. GitHub webhooks could provide real-time updates but are deferred.

### Neutral

- Multi-team review tracking is acknowledged but deferred.
- The `sla_remaining` resume-vs-reset policy is flagged for team discussion.
- `pr_tracking` records continue to be retained after closure for reporting.
