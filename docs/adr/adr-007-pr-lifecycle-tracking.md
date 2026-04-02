# ADR: PR Lifecycle Tracking

**Date:** 2026-03-24
**Status:** Accepted

---

## Context

[ADR-004](adr-004-pr-identification-and-sla-tracking.md) introduced PR detection and SLA tracking. The system calculates SLA deadlines from the PR creation date and triggers escalation purely on SLA expiry with the PR still open. It has no awareness of review activity.

This creates problems:

- **False escalations** — if the owning team has already reviewed and requested changes, the bot still escalates when the SLA expires, even though the ball is in the tenant's court.
- **No visibility into PR review state** — support engineers and dashboards can't distinguish between "waiting for review" and "waiting for tenant to address feedback."
- **SLA runs regardless of context** — the clock ticks even when the tenant is the blocker, making SLA metrics unreliable.
- **Tickets stay open after approval** — a PR approved and ready to merge still shows as an open support ticket until someone actually clicks merge.

ADR-004 explicitly deferred review-awareness to this follow-up. This ADR proposes incorporating GitHub review state into the PR lifecycle, pausing SLA tracking when the tenant is the blocker, and closing tickets when PRs are approved and mergeable.

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

## Options Considered

### GitHub webhooks vs. polling

Webhooks would provide real-time review state detection but require additional infrastructure (webhook receiver, secret management, retry handling) and a publicly reachable endpoint. Polling is simpler, consistent with the existing v1 approach, and sufficient for hour/day-scale SLAs. Webhooks can be added later if latency becomes a concern.

### SLA resume vs. SLA reset

When the SLA clock resumes after a pause (e.g., tenant addresses requested changes), the clock could either resume with the remaining time or reset to a fresh duration. Resuming remaining time is proposed as the default — it preserves the original SLA commitment. Resetting would be more forgiving but could allow indefinite SLA extension through repeated pause/resume cycles. This is flagged as an open question for team review.

### Close on approval vs. close only on merge

Closing the support ticket on approval (when the PR is mergeable) reduces open ticket noise and reflects that the owning team's obligation is fulfilled. The risk is that a PR sits approved-but-unmerged indefinitely, but at that point the blocker is the tenant, not the owning team. Closing only on merge is simpler but leaves tickets open unnecessarily when the owning team has done their part.

---

## Decision

### 1. GitHub Review State Awareness

The bot will consume review verdicts (approved, changes requested, commented) and PR mergeable status from GitHub during each poll cycle, in addition to the existing PR state check.

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

  * ─→ CLOSED (any state, if PR is merged or closed on GitHub)
```

### 3. SLA Clock Pausing and Resuming

The SLA clock pauses when the owning team is not the blocker and resumes when they are:

- When the tracking state transitions to `CHANGES_REQUESTED`, the SLA clock pauses. The remaining SLA duration is preserved. The owning team has acted; the tenant needs to address feedback.
- When the tracking state transitions to `APPROVED` (approved but not yet mergeable — e.g., CI still running or merge conflicts), the SLA clock pauses. The owning team has fulfilled their review obligation; the PR is blocked on external factors, not the owning team. If the PR is both approved and mergeable, it transitions directly to `CLOSED` (see section 5), so `APPROVED` is only reached when merge is not yet possible.
- When the tracking state transitions back to `OPEN` (e.g., tenant pushes new commits after changes were requested), the SLA clock resumes with the previously remaining duration.
- Escalation only triggers when the status is `OPEN` and the SLA deadline has passed — unchanged logic, but now only fires when the owning team is genuinely the blocker.

### 4. Approval Validation

Only approvals from the owning team trigger state transitions and ticket closure. Approvals from the PR author's own team or unrelated users are ignored.

Validation uses a two-step resolution strategy:

1. **Explicit team slug** — if `github-team-slug` is configured on the repository entry, the GitHub Teams API is used to resolve team members. This is the most reliable mechanism.
2. **Requested team reviewers** — if no slug is configured, the bot checks which teams GitHub has requested as reviewers on the PR (auto-assigned via CODEOWNERS or branch protection). Members of those teams are considered the owning team.

If neither step resolves team members (no slug configured, no teams requested, or API failure), all reviews are accepted without team filtering. This ensures backward compatibility for teams without CODEOWNERS or branch protection.

### 5. Ticket Closure Conditions

A support ticket is closed when all active closable PR tracking records for that ticket reach `CLOSED` status. A PR tracking record transitions to `CLOSED` when any of the following occurs:

| Condition | Outcome |
|-----------|---------|
| PR is merged | Closure notification (existing) |
| PR is closed (not merged) | Closure notification (existing) |
| PR is approved by owning team AND mergeable | Approval + ready-to-merge notification |

"Mergeable" means the GitHub API reports the PR as mergeable (checks passing, no blocking reviews outstanding, no merge conflicts). If the PR is approved but not yet mergeable, it remains in `APPROVED` state and will be checked again on the next poll cycle.

### 6. Polling Enhancements

The existing `PrLifecyclePoller` is extended to evaluate the new states on each poll cycle. Merge/close detection takes priority over review state changes. Transition logic follows the state diagram in section 2.

When a PR is first detected, the detection service fetches review state and sets the correct initial lifecycle status. If the owning team has already requested changes or approved, the record starts in `CHANGES_REQUESTED` or `APPROVED` rather than `OPEN`. Escalation only occurs at detection time when the SLA is breached **and** no actionable reviews exist.

### 7. Activity Tracking

To support "last time the team interacted with a PR" and dashboard reporting, the bot tracks key activity timestamps on each PR tracking record:

- `last_review_at` — timestamp of the most recent review from the owning team (approval, changes requested, or comment). Updated on each poll cycle from GitHub review data.
- `last_author_activity_at` — timestamp of the most recent push or comment from the PR author. Provisioned in the schema for future use; not yet populated (requires commit or event data not currently fetched).

### 8. Schema Changes

The `pr_tracking` table is extended with:

- Two new enum values: `CHANGES_REQUESTED` and `APPROVED` added to `pr_tracking_status`.
- `sla_remaining` — stores the remaining SLA duration when the clock is paused. `sla_deadline` becomes nullable (NULL when paused).
- `last_review_at` and `last_author_activity_at` — activity tracking timestamps.

### 9. Dashboard

The API will expose PR lifecycle state, SLA status, and activity timestamps per team, enabling a dashboard view in the support UI (Analytics & Operations tab). Dashboard design is separate.

### 10. Multi-Team Reviews

The current model tracks one owning team per PR. Multi-team review tracking will require either multiple tracking records per PR or a separate review model, to be designed as a follow-up.

---

## Resolved Questions

- **SLA resume vs. reset**: Resolved — the SLA clock resumes with remaining time (not reset).
- **Approval validation mechanism**: Resolved — explicit `github-team-slug` config, then GitHub requested team reviewers. See section 4.

---

## Consequences

### Positive

- Eliminates false escalations when the owning team has already reviewed.
- SLA metrics become meaningful — they measure time the owning team is the blocker, not total PR age.
- Tickets close earlier when PRs are approved and mergeable, reducing open ticket noise.
- Dashboard gives support engineers and team leads visibility into PR state across teams.
- Backward-compatible — existing `OPEN → ESCALATED → CLOSED` flow is preserved; new states are additive.

### Negative / Trade-offs

- **Increased GitHub API usage** — each poll cycle now fetches reviews and mergeable status in addition to PR state. Rate-limit impact should be monitored.
- **Team membership validation** — checking whether an approver belongs to the owning team requires additional API calls. The approach depends on available GitHub permissions.
- **SLA pause/resume adds complexity** — edge cases (e.g., multiple rapid state changes) need thorough testing.
- **No real-time state detection** — review state changes are detected at next poll interval. GitHub webhooks could provide real-time updates but are deferred.

### Neutral

- Multi-team review tracking is acknowledged but deferred.
- The `sla_remaining` resume-vs-reset policy is flagged for team discussion.
- `pr_tracking` records continue to be retained after closure for reporting.
