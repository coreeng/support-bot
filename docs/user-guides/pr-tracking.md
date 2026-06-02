# PR Tracking

PR tracking lets the support bot follow pull requests (GitHub) and merge
requests (GitLab) that tenants raise in support threads, keep an SLA clock on
the review, and chase the owning team in Slack when a review is overdue — so a
PR that needs a platform team's attention doesn't quietly rot.

This guide covers what the feature does, the providers it supports, how to
configure it, and how it shows up in Slack and the dashboard. Design rationale
lives in the ADRs ([adr-004][adr-004], [adr-006][adr-006], [adr-007][adr-007]).

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

## Configuration

All configuration lives under `pr-review-tracking` in
`api/service/src/main/resources/application.yaml`. Validation runs at startup
and fails fast on a bad config.

### Global settings

```yaml
pr-review-tracking:
  enabled: true                            # master feature flag
  poll-cron: "0 0 9-18 * * 1-5"            # lifecycle poller schedule (9–18 UTC, Mon–Fri)
  pr-emoji: pr                             # Slack reaction added to the original message
  tags: PR                                 # tag applied when the bot auto-closes the ticket
  impact: Information Request              # impact applied when the bot auto-closes the ticket
  sla-discovery:
    cache: PT24H                           # how long in-repo SLA files are cached

  github:
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    auth-mode:    ${GITHUB_AUTH_MODE:token}      # token | app
    token:        ${GITHUB_TOKEN:}               # PAT, when auth-mode=token
    app-id:           ${GITHUB_APP_ID:}          # GitHub App fields, when auth-mode=app
    installation-id:  ${GITHUB_APP_INSTALLATION_ID:}
    private-key-pem:  ${GITHUB_APP_PRIVATE_KEY_PEM:}

  gitlab:                                  # optional; only needed if a repo uses GitLab
    api-base-url: ${GITLAB_API_BASE_URL:https://gitlab.com/api/v4}
    token:        ${GITLAB_TOKEN:}
```

`tags` and `impact` must reference codes under `enums.tags` / `enums.impacts`,
and each repo's `owning-team` must reference a code under
`enums.escalation-teams`.

The `gitlab` block is optional. If any repository sets `provider: gitlab`, then
`gitlab.token` must resolve to a non-empty value (globally or via a per-repo
override), and `api-base-url` must include the `/api/v4` segment with no
trailing slash.

### Per-repository configuration

A repository is either **SLA-tracked** (has an `sla` block) or **no-SLA**
(has a `paths` list) — never both. No-SLA repos track only PRs touching the
listed paths and never escalate.

**GitHub, fixed SLA:**

```yaml
repositories:
  - name: my-org/my-repo
    # provider: github                    # implicit default
    owning-team: support-team
    github-team-slug: support-reviewers    # optional; falls back to the PR's requested teams
    sla:
      default: 48h
      overrides:                           # optional, path-specific
        - path: infra/**
          sla: 7d
```

**GitHub, SLA discovered from a file in the repo:**

```yaml
  - name: my-org/my-repo
    owning-team: support-team
    sla:
      file: .pr-sla.yaml
      default: 48h                         # fallback when the file is missing or invalid
```

**GitLab on gitlab.com:**

```yaml
  - name: my-group/my-project
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: my-group/reviewers  # approvals by this group's members count
    sla:
      default: 48h
```

**GitLab self-hosted, with a per-repo connection override:**

```yaml
  - name: platform/infra/cluster-config    # nested groups are supported
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: platform/reviewers
    gitlab:
      api-base-url: https://gitlab.internal.example.com/api/v4
      token:        ${GITLAB_INTERNAL_TOKEN}
    sla:
      file: .pr-sla.yaml
      default: 7d
```

**No-SLA, path-scoped (works for either provider):**

```yaml
  - name: my-org/no-sla-repo
    owning-team: support-team
    paths:
      - infra/**
      - rbac/**
```

Validation rules:
- `provider` is `github` (default) or `gitlab`.
- Repository names are unique. GitHub names have exactly one `/` (`org/repo`);
  GitLab names allow nested groups (`group/subgroup/project`).
- `github-team-slug` is only valid on GitHub repos; `gitlab-group-path` (and a
  per-repo `gitlab:` block) only on GitLab repos.
- `owning-team` must exist in `enums.escalation-teams`.
- A repo has **either** an `sla` block **or** a non-empty `paths` list.

---

## Slack integration

PR tracking is driven entirely from support threads — there's no separate
command surface.

- **Detection** happens on any message in a support thread that contains a
  tracked PR/MR URL. The bot reacts to the original message with `pr-emoji`
  and posts an acknowledgement reply in-thread.
- **Lifecycle events** (approved, changes requested, merged, closed,
  escalated) each post a reply in the same thread as they happen.
- **Escalation** additionally surfaces the PR to the owning team via the
  standard escalation flow when an SLA deadline is missed.
- **Auto-close**: once every tracked PR on the thread reaches `CLOSED`, the
  bot closes the support ticket with the configured `tags` and `impact`.

### Customising the messages

Each repo can override the default Slack copy for any event with a CEL
expression. Templates compile at startup; a bad template logs a warning and
falls back to the built-in default (the feature is fail-safe). The provider
only selects the **default** wording (GitHub says "PR #N", GitLab says
"MR !N") — a custom override always wins.

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
| `pr_url`        | string | Full PR/MR URL                                              |
| `repo_name`     | string | `org/repo` (GitHub) or `group/.../project` (GitLab)         |
| `repo_url`      | string | Full repository URL                                         |
| `owning_team`   | string | Team code from `enums.escalation-teams`                     |
| `sla_duration`  | string | e.g. `2 days`; empty for no-SLA repos                       |
| `sla_deadline`  | string | e.g. `Wed 08 May at 17:00 UTC`; empty for no-SLA repos      |
| `provider`      | string | `github` or `gitlab`                                        |

Setting an `escalated` message on a no-SLA repo is rejected at startup.

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
