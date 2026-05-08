# PR Tracking — GitLab Support (Spec)

Spec for extending the PR tracking feature ([pr-tracking.md][pr-tracking]) to
support GitLab merge requests alongside GitHub pull requests. Tracks PT-439.

[pr-tracking]: ./pr-tracking.md

> **Status:** draft, pending implementation. Decisions in the table below are
> locked unless explicitly reopened.

---

## Goals

- Detect GitLab MR URLs in support threads and track them through the same
  lifecycle as GitHub PRs (detected → approved → merged/closed → optionally
  escalated).
- Support both `gitlab.com` and self-hosted GitLab instances via a per-repo
  base URL, with a sensible global default.
- Reuse the existing SLA, escalation, polling, persistence, and Slack
  messaging machinery — GitLab is a new *source*, not a parallel feature.
- Keep existing GitHub configuration and behaviour 100% backwards compatible.

## Non-goals (v1)

- GitLab `CHANGES_REQUESTED` parity. GitLab has no canonical "request changes"
  state across versions, and the user-facing pause/resume semantics are
  GitHub-specific in v1. GitLab MRs go straight `OPEN → APPROVED` (or
  `MERGED`/`CLOSED`/`ESCALATED`); the SLA clock never auto-pauses.
- OAuth / GitLab Application auth. Personal / Project / Group access tokens
  only.
- Approval-rule-aware reviewer filtering. v1 maps "owning team" to a single
  GitLab group path; membership in that group counts as a qualifying
  approval.
- UI changes. The dashboard treats GitLab MRs identically to GitHub PRs;
  provider is not surfaced as a column or filter in v1.
- Renaming `pr-emoji`, `pr_number`, `PrTrackingProps`, etc. We keep "PR" as
  the internal noun for both providers.

---

## Locked design decisions

| Decision                       | Choice                                                                |
|--------------------------------|------------------------------------------------------------------------|
| Hosting                        | Both gitlab.com and self-hosted, per-repo base URL                    |
| Internal terminology           | Keep "PR" everywhere (config keys, CEL vars, Java types)              |
| Provider field                 | Explicit `provider: github \| gitlab` per repo; default `github`      |
| GitLab review states (v1)      | `APPROVED`, `MERGED`, `CLOSED`, `ESCALATED`. No `CHANGES_REQUESTED`.   |
| GitLab auth                    | PAT only (Personal / Project / Group access token)                    |
| Reviewer filter                | GitLab group path; approval counts iff approver is in the group       |
| Schema                         | Add `provider` column AND rename `github_repo` → `repo`                |
| SLA discovery                  | YAML-configured **and** in-repo `.pr-sla.yaml` (parity with GitHub)   |
| Connection config              | Global default + per-repo override                                    |
| Default Slack messages         | Provider-specific defaults: "PR" for GitHub, "MR" for GitLab          |
| UI changes (v1)                | None — REST DTO field names preserved for backwards compatibility     |

---

## Configuration

All new config sits inside the existing `pr-review-tracking` block.

### Global GitLab settings

```yaml
pr-review-tracking:
  # ... existing global keys (enabled, poll-cron, pr-emoji, tags, impact, ...) unchanged ...

  github:
    # ... existing block unchanged ...

  gitlab:
    api-base-url: ${GITLAB_API_BASE_URL:https://gitlab.com/api/v4}
    token:        ${GITLAB_TOKEN:}
```

The `gitlab` block is **optional**. Validation:
- If any repo has `provider: gitlab`, `gitlab.token` must resolve to a
  non-empty value (either globally or via per-repo override).
- `api-base-url` must end without a trailing slash and must include the
  `/api/v4` segment (we don't auto-append it — explicit > magic).

### Per-repository configuration

#### GitHub (unchanged)

```yaml
repositories:
  - name: my-org/my-repo
    # provider: github                # implicit default
    owning-team: support-team
    github-team-slug: support-reviewers
    sla:
      default: 48h
```

#### GitLab — gitlab.com, default token

```yaml
  - name: my-group/my-project
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: my-group/reviewers
    sla:
      default: 48h
```

#### GitLab — self-hosted, with overrides

```yaml
  - name: platform/infra/cluster-config       # group/subgroup/project
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

#### GitLab — no-SLA, path-scoped

```yaml
  - name: my-group/my-project
    provider: gitlab
    owning-team: support-team
    gitlab-group-path: my-group/reviewers
    paths:
      - infra/**
```

### Validation rules (additions to `PrTrackingProps`)

- `provider` ∈ `{github, gitlab}`. Default `github`.
- `github-team-slug` is permitted only when `provider: github`. Forbidden on
  GitLab repos.
- `gitlab-group-path` is permitted only when `provider: gitlab`. Forbidden on
  GitHub repos.
- `gitlab.api-base-url` and `gitlab.token` (per-repo block) are permitted only
  when `provider: gitlab`.
- Repository `name` shape:
  - GitHub: exactly one `/`, e.g. `org/repo`.
  - GitLab: at least one `/`, allowing nested groups (`group/subgroup/project`).
- All other existing rules (mutual exclusion of `sla` vs `paths`, no-SLA repos
  must have non-empty `paths`, escalation team must exist, etc.) carry over
  unchanged.

---

## Provider abstraction

Introduce a thin port and two adapters:

```
prtracking/
  source/
    PrSourceClient.java          # interface (port)
    GitHubPrSourceClient.java    # wraps existing GitHub client
    GitLabPrSourceClient.java    # new
    PrSourceClients.java         # provider → client lookup, keyed by repo
```

`PrSourceClient` exposes only what detection, the lifecycle poller, and SLA
discovery actually need:

```java
interface PrSourceClient {
    PrMetadata fetchMetadata(RepoCoord repo, int prNumber);
    List<Review> fetchReviews(RepoCoord repo, int prNumber);
    Optional<String> fetchFileContents(RepoCoord repo, String ref, String path);
}
```

`RepoCoord` carries `(provider, name, baseUrl)` so adapters don't reach into
config. Existing GitHub call sites become calls through the port; the
`GitHubPrSourceClient` adapter is a near-trivial wrapper around the current
client. Tests for the port use canned `PrSourceClient` doubles.

---

## URL detection

`GitHubPrUrlParser` becomes one of two parsers behind a dispatcher:

- `GitHubPrUrlParser` — unchanged shape: `https://github.com/{org}/{repo}/pull/{n}`.
- `GitLabMrUrlParser` — `https://{host}/{group}(/.../subgroup)/{project}/-/merge_requests/{n}`.
  - Allowed hosts derived from `pr-review-tracking.gitlab.api-base-url` and
    every per-repo `gitlab.api-base-url` override (host part only).
  - Multi-level groups are supported; the parser greedily consumes path
    segments up to `/-/merge_requests/`.
- `PrUrlDispatcher` returns a `(provider, RepoCoord, prNumber)` triple, or
  empty if no parser matches.

Detection short-circuits unknown providers; URL parsing must never throw.

---

## Lifecycle on GitLab

States persisted in `pr_tracking.status` are unchanged. GitLab transitions in
v1:

| From       | To           | Trigger                                                              |
|------------|--------------|-----------------------------------------------------------------------|
| `OPEN`     | `APPROVED`   | A user in `gitlab-group-path` appears in `approved_by`                |
| `OPEN`     | `MERGED`     | MR `state == "merged"`                                                |
| `OPEN`     | `CLOSED`     | MR `state == "closed"` and not merged                                 |
| `OPEN`     | `ESCALATED`  | SLA deadline passes (same logic as GitHub)                            |
| `APPROVED` | `MERGED`/`CLOSED` | as above                                                          |
| `ESCALATED`| `MERGED`/`CLOSED` | as above                                                          |

There is no `CHANGES_REQUESTED` path on GitLab. As a result:
- `pause_sla` / `resume_sla` repository methods are never invoked for GitLab
  rows.
- `sla_remaining` and `last_author_activity_at` will remain NULL for the
  lifetime of a GitLab tracking row. Existing CHECK constraints already allow
  this (only one of `sla_deadline` / `sla_remaining` may be set).

### GitLab APIs used (reference)

| Operation                      | Endpoint                                                          |
|--------------------------------|--------------------------------------------------------------------|
| Fetch MR metadata              | `GET /projects/:id/merge_requests/:iid`                            |
| Fetch approvals                | `GET /projects/:id/merge_requests/:iid/approvals`                  |
| Fetch group members (cached)   | `GET /groups/:id/members/all?per_page=100`                         |
| Fetch repo file (SLA discovery)| `GET /projects/:id/repository/files/:path?ref=:ref` (`raw` variant)|

Project and group lookups use URL-encoded paths (`my-group%2Fmy-project`) to
avoid an extra resolution round-trip.

### Reviewer filtering (`TeamReviewFilter` on GitLab)

The existing `TeamReviewFilter` becomes provider-aware. GitLab variant:

1. Resolve `gitlab-group-path` to its member set (cached per group, TTL = the
   existing `sla-discovery.cache` value or a new
   `gitlab.member-cache: PT24H` — open question, see below).
2. Intersect with `approved_by`. Any non-empty intersection ⇒ `APPROVED`.
3. Inherited group members (`/members/all`) are included, matching GitHub
   team semantics where nested team membership counts.

---

## SLA discovery on GitLab

`SlaLookup` already delegates the file fetch to a content client. We add a
GitLab implementation behind the `PrSourceClient.fetchFileContents` method,
calling the GitLab Repository Files API with the project's default branch.
Parsing of the returned YAML is provider-agnostic and unchanged. Existing
24h cache (keyed by `(provider, repo, ref, path)`) applies.

---

## Persistence

New migration: `V30__pr_tracking_provider_and_rename.sql`.

```sql
ALTER TABLE pr_tracking ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'github';
ALTER TABLE pr_tracking RENAME COLUMN github_repo TO repo;

ALTER TABLE pr_tracking DROP CONSTRAINT pr_tracking_ticket_repo_pr_unique;
ALTER TABLE pr_tracking
  ADD CONSTRAINT pr_tracking_ticket_provider_repo_pr_unique
  UNIQUE (ticket_id, provider, repo, pr_number);

-- Drop default once existing rows are backfilled
ALTER TABLE pr_tracking ALTER COLUMN provider DROP DEFAULT;

CREATE INDEX IF NOT EXISTS pr_tracking_provider_repo_idx
  ON pr_tracking (provider, repo);
```

Java side:
- `PrTrackingRecord` gets a `Provider provider` field; `githubRepo` → `repo`.
- `JdbcPrTrackingRepository` updates SQL accordingly.
- All `findBy*` overloads that took `(repo, prNumber)` now take
  `(provider, repo, prNumber)` to disambiguate identical names across
  providers (theoretical but cheap to enforce).

REST DTOs (`RepoInsights`, `InFlightPr`) keep their **JSON** field names —
i.e. `githubRepo` continues to appear over the wire — using Jackson
`@JsonProperty` so the v1 UI is unaffected. A future ticket can rename
those alongside UI updates.

---

## Slack messages

`PrMessageRenderer` resolves the default template per `(event, provider)`:

| Event               | GitHub default                                    | GitLab default                                    |
|---------------------|---------------------------------------------------|---------------------------------------------------|
| `DETECTED`          | "PR #N detected. SLA: …"                          | "MR !N detected. SLA: …"                          |
| `APPROVED`          | "PR #N approved …"                                | "MR !N approved …"                                |
| `MERGED`            | "PR #N merged. Thanks!"                           | "MR !N merged. Thanks!"                           |
| `CLOSED`            | "PR #N closed."                                   | "MR !N closed."                                   |
| `CHANGES_REQUESTED` | "Changes requested on PR #N …"                    | _unused on GitLab_                                |
| `ESCALATED`         | (existing escalation copy)                        | (existing escalation copy)                        |

Custom `messages:` overrides on a repo win as today; the provider only
selects the **default**.

CEL variables get one new entry:

| Variable    | Type   | Notes                              |
|-------------|--------|------------------------------------|
| `provider`  | string | `github` or `gitlab`               |

`pr_number`, `pr_url`, `repo_name`, `repo_url`, `owning_team`, `sla_*`
unchanged.

---

## Functional tests

- Add a `gitlab/` wiremock fixture set under `api/functional/...` mirroring
  the existing `github/` fixtures: MR metadata, approvals, group members,
  raw file fetch.
- Extend `PrTrackingFunctionalTests` with GitLab cases for: detection,
  approval transition, SLA escalation, merge, close, no-SLA path-scoped
  repo, and in-repo `.pr-sla.yaml` discovery.
- The test-only controller (`PrTrackingTestController`) does not need
  per-provider branches — it operates on the persisted records directly.

---

## Backwards compatibility

- Existing config files keep working without edits. `provider` defaults to
  `github`; the GitHub block is unchanged.
- Existing DB rows are migrated in place (`provider = 'github'`). The unique
  constraint is rebuilt to include `provider`; logically equivalent for
  GitHub-only deployments.
- REST/UI surfaces are unchanged in v1: same paths, same JSON field names.
- The `pr-review-tracking.gitlab` block is optional; deployments that don't
  use GitLab don't need to add anything.

Rollout order:
1. Ship migration + provider-aware schema and JDBC layer (no functional
   change for GitHub-only deployments).
2. Ship `PrSourceClient` port + GitHub adapter refactor.
3. Ship GitLab adapter, URL parser, group-membership resolver, and
   provider-aware message renderer behind config (no GitLab repos in
   `application.yaml` until verified).
4. Add a single GitLab repo to `application.yaml` to enable in production.

---

## Open questions

1. **Group membership cache TTL.** Reuse `sla-discovery.cache` (24h) or
   introduce a separate `gitlab.member-cache` knob? Default proposal: reuse,
   add the dedicated knob only if operators ask.
2. **Stale-token handling.** GitLab returns 401 on expired PATs; GitHub
   surfaces this through its existing retry/backoff. Confirm we want the same
   "log + skip this poll cycle" behaviour for GitLab, or escalate via a
   health endpoint.
3. **Rate limits.** gitlab.com enforces 2,000 req/min/user by default.
   Lifecycle polling × member fetches per repo should stay well under, but
   worth a back-of-envelope check before adding many GitLab repos.
4. **Multiple GitLab tokens per instance.** v1 supports one token per
   instance (global or per-repo override). If two repos on the same instance
   need different tokens (e.g. a project-access-token per project), the
   per-repo override already covers it — flagging here in case the assumption
   is wrong.
