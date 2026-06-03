# PR Tracking

PR tracking lets the support bot follow pull requests (GitHub) and merge
requests (GitLab) that tenants raise in support threads, keep an SLA clock on
the review, and chase the owning team in Slack when a review is overdue — so a
PR that needs a platform team's attention doesn't quietly rot.

This guide is for **end users** — people raising and following support threads.
It covers what the feature does, the providers it supports, and how it shows up
in Slack and the dashboard. **Operators** who deploy and configure the bot
should see the [PR review tracking configuration reference][config-ref] (enabling
the feature, token permissions, per-repository settings, SLA and message
customisation). Design rationale lives in the ADRs ([adr-004][adr-004],
[adr-006][adr-006], [adr-007][adr-007]).

[config-ref]: ../../api/service/docs/configuration.md#pr-review-tracking
[adr-004]: ../adr/adr-004-pr-identification-and-sla-tracking.md
[adr-006]: ../adr/adr-006-repository-sla-discovery.md
[adr-007]: ../adr/adr-007-pr-lifecycle-tracking.md

---

## What it does

When a tenant posts a PR/MR URL in a support thread, the bot:

1. **Detects** the link, fetches the PR's metadata, and starts tracking it.
2. **Replies in-thread** with an SLA acknowledgement (or a no-SLA note).
3. **Polls the provider** on a schedule for review-state changes (approved,
   changes requested, merged, closed).
4. **Pauses / resumes the SLA clock** automatically as the PR moves between
   "waiting on review" and "waiting on the author" (GitHub only — see
   [Provider differences](#provider-differences)).
5. **Escalates** to the owning team when the SLA deadline passes without an
   approval.
6. **Auto-closes the support ticket** once every tracked PR on the thread is
   resolved.

The whole pipeline is gated by a single feature flag. When disabled, none of
its beans, schedulers, or REST endpoints are created.

### Lifecycle states

A tracked PR moves through these states (persisted in `pr_tracking.status`):

| State               | Meaning                                                      |
|---------------------|-------------------------------------------------------------|
| `OPEN`              | Detected, waiting on review; SLA clock running              |
| `CHANGES_REQUESTED` | Owning team requested changes; SLA clock paused (GitHub)    |
| `APPROVED`          | Owning team approved; ready to merge                        |
| `ESCALATED`         | SLA deadline passed without approval — owning team chased   |
| `CLOSED`            | PR was merged or closed                                     |

`ESCALATED` only applies to repositories that have an SLA configured; for
no-SLA repos it never fires.

---

## Providers

The bot tracks two providers behind one shared SLA / escalation / Slack
pipeline. Each repository declares its `provider`; the default is `github`, so
existing GitHub-only configuration keeps working unchanged.

| Provider | URL form tracked                                                    | Auth                          |
|----------|---------------------------------------------------------------------|-------------------------------|
| GitHub   | `https://github.com/{org}/{repo}/pull/{n}`                          | PAT or GitHub App             |
| GitLab   | `https://{host}/{group}/.../{project}/-/merge_requests/{n}`         | Personal/Project/Group token  |

GitLab works against both `gitlab.com` and self-hosted instances (per-repo
base URL), and supports nested groups (`group/subgroup/project`).

### Provider differences

GitLab is tracked through the **same** lifecycle, with two intentional
differences in v1:

- **No `CHANGES_REQUESTED` state.** GitLab has no canonical "request changes"
  signal, so MRs go straight `OPEN → APPROVED` (or `MERGED` / `CLOSED` /
  `ESCALATED`). The SLA clock never auto-pauses for a GitLab MR.
- **Approval = group membership.** "Owning team" maps to a single GitLab group
  path; an approval by anyone in that group (including inherited members)
  counts as a qualifying review.

Everything else — SLA discovery, escalation, ticket auto-close, the dashboard —
is identical across providers.

---

## Slack integration

PR tracking is driven entirely from support threads — there's no separate
command surface.

- **Detection** happens on any message in a support thread that contains a
  tracked PR/MR URL. The bot reacts to the original message with the configured
  emoji and posts an acknowledgement reply in-thread.
- **Lifecycle events** (approved, changes requested, merged, closed,
  escalated) each post a reply in the same thread as they happen.
- **Escalation** additionally surfaces the PR to the owning team via the
  standard escalation flow when an SLA deadline is missed.
- **Auto-close**: once every tracked PR on the thread reaches `CLOSED`, the
  bot closes the support ticket.

### Customising the messages

The Slack copy for each lifecycle event can be tailored per repository by an
operator. The provider only selects the **default** wording (GitHub says
"PR #N", GitLab says "MR !N") — a custom override always wins. See
[Customising the Slack messages][config-msgs] in the configuration reference.

[config-msgs]: ../../api/service/docs/configuration.md#customising-the-slack-messages

---

## Dashboard

PR tracking also surfaces in the **Tenant Requests** dashboard in the UI:

- **PR Activity & SLA Health** — per-repo stats: PR count, open, escalated,
  SLA-breached, and p50/p90/p99 time-to-resolution, with a date-range filter.
- **In-Flight PRs** — every active tracked PR with its SLA badge (active /
  paused / breached / no-SLA), age, what it's waiting on, and a deep link back
  to the Slack thread; filterable by team.

The dashboard hides itself automatically when PR tracking is disabled. GitHub
and GitLab PRs appear in the same tables (provider is not a separate column or
filter in v1).
