# PR Tracking — GitLab Support (Implementation Plan)

Execution plan for the GitLab MR support work specified in
[pr-tracking-gitlab-spec.md][spec]. Tracks PT-439.

[spec]: ./pr-tracking-gitlab-spec.md

> **Approach:** all six commits land on
> `pt-439-gitlab-merge-request-tracking` and ship as a single PR to `main`.
> Commit boundaries below are the review structure — keep them clean so a
> reviewer can read the PR commit-by-commit if they want.

---

## Resolved implementation questions

These were left open by the spec; defaulting them here so they don't block
work. Each can be revisited in the commit that touches the relevant code.

| Question                                          | Decision                                                                  |
|---------------------------------------------------|----------------------------------------------------------------------------|
| GitLab `detailed_merge_status` → `isMergeable`    | Treat `mergeable` and `ci_still_running` as mergeable. Mirrors GitHub's `MERGEABLE` ignoring required checks. |
| SLA file fetch `ref` on GitLab                    | Use the project's `default_branch` from MR metadata. Deterministic; one extra field to read. |
| Group-membership cache TTL                        | Reuse `pr-review-tracking.sla-discovery.cache` (24h). Add a dedicated knob only if operators ask. |

---

## Commit 1 — `refactor(pr-tracking): introduce PrSourceClient port and migrate GitHub call sites`

**Goal:** Wrap the existing GitHub client behind a provider-neutral
`PrSourceClient` port. Pure refactor; behaviour unchanged.

**Files**
- `prtracking/source/PrSourceClient.java` (new) — port interface. Lock the
  full neutral contract here: `RepoCoord` value object, `Provider` enum,
  `PrMetadata` neutral DTO, `Review` neutral DTO. Methods: `fetchMetadata`,
  `fetchReviews`, `fetchFileContents`, `listFiles`, `resolveTeamMembers`.
- `prtracking/source/PrSourceClients.java` (new) — `Provider` → client
  lookup. In this commit only `GITHUB` is registered.
- `prtracking/source/GitHubPrSourceClient.java` (new) — thin delegator over
  the existing `GitHubClient`; maps GitHub responses into the neutral DTOs.
- `prtracking/PrDetectionService.java`, `PrLifecyclePoller.java`,
  `SlaLookup.java`, `TeamReviewFilter.java` — call through the port instead
  of `GitHubClient`. Pass `Provider.GITHUB` everywhere for now.
- `prtracking/PrTrackingGitHubConfig.java` — register beans.

**Key design notes**
- Land `PrMetadata` and `RepoCoord` here, not later. The whole plan rests on
  this seam being complete from day 1; the GitLab adapter (commit 4) must
  not have to widen the contract.
- `TeamReviewFilter` currently consults `pr.requestedTeamReviewerLogins()`
  for the GitHub fallback path. Keep that logic inside the GitHub adapter;
  expose it via `resolveTeamMembers(RepoCoord, ownerSpec)` so the GitLab
  adapter (which has no such fallback) can return an empty/explicit-only
  result without contortions.
- Don't modify `GitHubClient` itself — keeps blast radius small.

**Risks / rollback**
Pure refactor. Revert by removing the new package and restoring the direct
`GitHubClient` call sites. No DB or wire-format change.

---

## Commit 2 — `feat(pr-tracking): add provider column and rename github_repo to repo`

**Goal:** Land the schema change and propagate it through Java + REST DTOs
while everything still hardcodes `provider=GITHUB`.

**Files**
- `db/migration/V30__pr_tracking_provider_and_rename.sql` (new) — the SQL
  from the spec: add `provider` with default `'github'`, rename column,
  rebuild unique constraint to include `provider`, drop the default,
  index `(provider, repo)`.
- `prtracking/PrTrackingRecord.java`, `NewPrTracking.java` — add
  `Provider provider`; rename `githubRepo` → `repo`.
- `prtracking/PrTrackingRepository.java`,
  `prtracking/JdbcPrTrackingRepository.java` — column rename in SQL,
  insert/select `provider`, update `existsByTicketIdAndRepoAndPrNumber` to
  also take `provider`, propagate through `findAllInFlight` /
  `getInsightsByRepo` / `getEscalationBreakdown`.
- `prtracking/rest/InFlightPr.java`, `RepoInsights.java`,
  `InFlightPrResponse.java` — internal field becomes `repo`; preserve the
  wire format with `@JsonProperty("githubRepo")`. Add `provider` field but
  do **not** serialize it in v1.
- `prtracking/rest/PrTrackingTestController.java` — add optional `provider`
  to the request DTO, default `github`.
- `prtracking/PrDetectionService.java`, `PrLifecyclePoller.java` — pass
  `Provider.GITHUB`; update logging field names.
- jOOQ regeneration of `dbschema.tables.PrTracking` (build-time).

**Key design notes**
- This commit is the **atomic rename**. The jOOQ-generated
  `PR_TRACKING.GITHUB_REPO` constant becomes `PR_TRACKING.REPO`; every
  reference must move together or the build breaks.
- The `@JsonProperty("githubRepo")` rename is load-bearing for the v1 "no
  UI changes" guarantee. UI `in-flight-prs.tsx` reads `pr.githubRepo` in
  several places — verify the wire JSON shape against the existing
  functional-test fixtures before pushing.
- The V13 CHECK constraint (`sla_deadline` and `sla_remaining` are mutually
  exclusive) is unchanged; GitLab rows will leave both NULL paths free,
  which the constraint already permits.
- `findAllInFlight` builds GitHub URLs in Java code today. Leave the GitHub
  URL builder as-is in this commit; commit 5 introduces provider-aware URL
  building via config-side resolution (no extra column needed).
- `replaceHasSlaWithCurrentConfig` keys on lowercase repo name. Update it
  to key on `(provider, name)` here so commit 5 doesn't have to.

**Risks / rollback**
Forward-only migration. A reversal would need a V31. Mitigate by staging
on a non-prod DB; the default-then-drop pattern means existing rows
survive untouched. Wire-format breakage from a missed Jackson rename is
the main exposure — covered by the existing functional-test JSON
assertions and a manual UI smoke before push.

---

## Commit 3 — `feat(pr-tracking): add gitlab config block and provider-aware repository validation`

**Goal:** Extend `PrTrackingProps` and YAML to model GitLab repos and the
global `gitlab` block. No runtime wiring yet.

**Files**
- `config/PrTrackingProps.java` — additive:
  - `Provider provider` on `Repository` (default `github`).
  - `gitlabGroupPath` on `Repository`.
  - Per-repo `Gitlab` override block (`apiBaseUrl`, `token`).
  - Top-level `Gitlab gitlab` record (`apiBaseUrl`, `token`).
  - Validation per spec §"Validation rules": forbid `githubTeamSlug` on
    GitLab repos; forbid `gitlabGroupPath` on GitHub repos; relax the
    `org/repo` shape check to "≥ 1 slash" for GitLab repos; require a
    resolvable `gitlab.token` (global or per-repo) when any repo is GitLab;
    enforce no-trailing-slash and `/api/v4` segment on `api-base-url`.
- `application.yaml` — comment-only example for the `gitlab:` global block
  and a sample GitLab repo entry. Do not enable.

**Key design notes**
- `normalizeRepositoryName` lower-cases — keep that for both providers;
  GitLab repo paths are case-insensitive in URL form too.
- The canonical `Repository` constructor needs the new fields with
  defaults; the Spring `@ConstructorBinding` overload must stay
  binary-compatible. Add a convenience constructor for tests if they
  currently build `Repository` by hand.
- Validation runs in the record constructor today. Keep that; it ensures
  failures land at startup before any provider-specific bean construction.

**Risks / rollback**
Config-only. Existing YAML continues to bind unchanged. Revert.

---

## Commit 4 — `feat(pr-tracking): add GitLab source client and group membership resolver`

**Goal:** Implement the GitLab adapter and group-member cache. Still
unwired from detection/poller — `PrSourceClients` registers it, but no
repo has `provider: gitlab` yet.

**Files**
- `prtracking/source/GitLabPrSourceClient.java` (new) — Spring `RestClient`
  (or whatever HTTP scaffolding the repo already uses; check first). Reads
  global token + base URL, with per-repo overrides. URL-encodes project
  and group paths. Implements:
  - `fetchMetadata` via `GET /projects/:id/merge_requests/:iid`, mapping
    `state`, `merge_status` / `detailed_merge_status`, `default_branch`,
    timestamps.
  - `fetchReviews` via `GET /projects/:id/merge_requests/:iid/approvals`,
    yielding a synthetic `Review` per approver.
  - `fetchFileContents` via the Repository Files raw API on the project's
    `default_branch`.
- `prtracking/source/GitLabApiException.java` (new) — sibling to
  `GitHubApiException`; surfaces 401/404 distinctly.
- `prtracking/source/GitLabGroupMemberCache.java` (new) — Caffeine cache,
  TTL = `slaDiscovery.cache`, keyed by `(apiBaseUrl, groupPath)`. Calls
  `GET /groups/:id/members/all?per_page=100` (paginates).
- `prtracking/PrTrackingGitLabConfig.java` (new) —
  `@ConditionalOnProperty("pr-review-tracking.enabled")`; constructs the
  GitLab client and member cache, registers them in `PrSourceClients`.
  No-op when no GitLab repos are configured (so a missing token doesn't
  fail startup unless commit 3's validation already required it).

**Key design notes**
- `isMergeable` mapping: see resolved questions table.
- 401/404 handling: parity with GitHub — log + skip this poll cycle. Don't
  invent a new health endpoint in v1.
- Group resolver returns the union of direct + inherited members
  (`/members/all`), matching GitHub team semantics where nested membership
  counts.

**Risks / rollback**
Adapter is unused in this commit. No production effect. Revert.

---

## Commit 5 — `feat(pr-tracking): wire GitLab MR detection, lifecycle, messages, and tests`

**Goal:** End-to-end wiring plus functional-test parity. The largest
behavioural commit.

**Files**
- `prtracking/GitLabMrUrlParser.java` (new) — host set derived from the
  global `gitlab.api-base-url` plus every per-repo override. Greedy
  `/-/merge_requests/N` match accepting nested groups
  (`group/subgroup/project`).
- `prtracking/PrUrlDispatcher.java` (new) — fans out to the GitHub and
  GitLab parsers; returns `List<DetectedPr>` with `provider` populated.
- `prtracking/DetectedPr.java` — add `provider`.
- `prtracking/PrTrackingConfig.java` — register the dispatcher; pass the
  set of allowed GitLab hosts.
- `prtracking/PrDetectionService.java` — replace direct
  `GitHubPrUrlParser` field with the dispatcher. Route metadata / file /
  review fetches through `PrSourceClients.forProvider(provider)`. Skip the
  `pause_sla` paths for GitLab repos.
- `prtracking/PrLifecyclePoller.java` — provider-aware routing. For
  GitLab records, only `OPEN → APPROVED | MERGED | CLOSED | ESCALATED`
  transitions execute. The OPEN→CHANGES_REQUESTED branch and
  `processChangesRequestedRecord` are gated to GitHub.
- `prtracking/TeamReviewFilter.java` — provider-aware. GitHub keeps
  existing logic. GitLab resolves `gitlabGroupPath` via the cache from
  commit 4 and intersects with the MR's `approved_by`; non-empty
  intersection synthesises a single APPROVED `Review`.
- `prtracking/PrMessageRenderer.java` — defaults table keyed by
  `(event, provider)`. "PR" vs "MR", `#N` vs `!N`. Custom `messages:`
  overrides win as today. Add CEL var `provider`.
- `prtracking/PrMessageContext.java` — add `provider`.
- `prtracking/JdbcPrTrackingRepository.java` — `findAllInFlight` URL
  builder becomes provider-aware. Resolve via in-memory config (matches
  how `replaceHasSlaWithCurrentConfig` works); no extra column needed.
- `api/functional/.../wiremock/gitlab/` (new) — MR metadata, approvals,
  group members, raw file fetch fixtures mirroring the GitHub set.
- `api/functional/.../PrTrackingFunctionalTests.java` — `@Nested` GitLab
  class covering: detection, group-member approval transition, SLA
  escalation, merge, close, no-SLA path-scoped repo, and in-repo
  `.pr-sla.yaml` discovery. Reuses the existing test base. Asserts on the
  persisted `provider` column (the test controller does not need a GitLab
  branch).

**Key design notes**
- `PrLifecyclePoller#findRepoConfig` currently does
  `equalsIgnoreCase(name)` only. Change it to match on `(provider, name)`
  here so a hypothetical name collision across providers can't pick the
  wrong config.
- `last_review_at` for GitLab: set to the most recent `approved_at` if the
  approvals payload exposes it, else leave NULL. Don't synthesise.
- The functional-test wiremock setup is the bulkiest part of this commit.
  Lift any helpers that get copy-pasted across GitHub and GitLab fixtures
  into a small shared utility — but only if it falls out naturally; don't
  let scaffolding-cleanup balloon the diff.

**Risks / rollback**
Largest behaviour-change commit. Rollback is removing the GitLab branch
in routing — rows persisted with `provider='gitlab'` would orphan, but
no GitLab repo will be in production config until commit 6.

---

## Commit 6 — `chore(pr-tracking): enable a single GitLab repo in application.yaml`

**Goal:** Production rollout step 4 from the spec — flip on for one canary
repo.

**Files**
- `application.yaml` — one GitLab repo entry; `GITLAB_TOKEN` plumbed
  through deploy config in the usual way.

**Risks / rollback**
Config change. Rollback by removing the entry.

---

## Cross-cutting risks

- **Commit 2 must land atomically.** The schema rename, jOOQ regeneration,
  every JDBC SQL string, both REST DTOs (with the Jackson rename), the
  test controller, and the functional tests' JSON assertions all move
  together. Piecemeal does not compile.
- **Commit 1 is the seam.** If `PrSourceClient` / `PrMetadata` /
  `RepoCoord` are not complete from the start, commit 4 has to widen the
  contract and commit 5 inherits the consequences. Worth careful review.
- **UI compatibility hinges on Jackson `@JsonProperty("githubRepo")`.**
  A missed annotation breaks the in-flight tab silently. The functional
  tests catch the JSON shape, but a manual UI smoke before push is
  cheap insurance.
- **Group-member cache TTL coupling.** Reuses `slaDiscovery.cache`. If
  that decision is reversed later, commit 4 grows a new prop; otherwise no
  knock-on effect.

---

## Out of scope (per spec §Non-goals)

- GitLab `CHANGES_REQUESTED` parity (no SLA pause/resume on GitLab).
- OAuth / GitLab Application auth.
- Approval-rule-aware reviewer filtering.
- UI changes (provider column / filter, JSON field rename to `repo`).
- Renaming `pr-emoji`, `pr_number`, `PrTrackingProps`, etc.
