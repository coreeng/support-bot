# Implementation plan — PR SLAs for code-owner repos

**Status:** Draft · **Builds on:** the PR-tracking foundation (declarative FSM table, #287) and the
author-admission gate (#288). This is workstream **C** from `docs/spikes/pr-tracking-enhancements.md`.

## Goal

For repos flagged `requires-codeowners`:

1. At detection, tell the tenant they may need to **chase the code owner** (not the maintaining team).
2. **Start the escalation clock only after the code owners have approved** — before that the clock is
   held and the code owner is chased.
3. Once code-owner-approved **and** mergeable, do **not** close — chase the maintaining (owning) team
   to merge, and close **only when the provider reports the PR merged**.

## Sourcing principle (do not re-implement CODEOWNERS)

We never parse the CODEOWNERS file or do path/glob matching. Both providers already compute the
per-folder matching (the client case is "one repo, many folders, each with its own owners") and expose
the *result*. The repos rely on branch protection that requires code-owner review, which makes these
signals authoritative:

- **GitHub** — a single GraphQL query per poll:

  ```graphql
  pullRequest(number: $n) {
    reviewDecision                                  # APPROVED | CHANGES_REQUESTED | REVIEW_REQUIRED
    reviewRequests(first: 100) { nodes {
      asCodeOwner                                   # true => requested via CODEOWNERS
      requestedReviewer { ... on User { login } ... on Team { slug } }
    }}
  }
  ```

  `reviewDecision == APPROVED` ⟹ all required code owners have approved (GitHub applies the
  "at least one owner per matched rule" semantics for us). `asCodeOwner: true` reviewers still present
  in `reviewRequests` are exactly the owners still owed a review — the chase list — since a reviewer
  drops off once they approve. This replaces "filter requested reviewers by the CODEOWNERS file": GitHub
  has already done the filtering.

  `reviewDecision` is chosen over the REST `mergeable_state == "blocked"` because the latter conflates
  failing CI / out-of-date branch with missing reviews; `reviewDecision` isolates the review dimension.
  `reviewDecision` is GraphQL-only (the hub4j client is REST), so this is a new client surface.

- **GitLab** — `GET /merge_requests/:iid/approval_state`: read the system-generated `code_owner` rule
  (`approved` = gate; `eligible_approvers` = chase list). Already REST, via the existing client. Note
  GitLab Code Owners is a Premium/Ultimate feature — validate/no-op where unavailable.

## Lifecycle changes

Two new persisted statuses on the `pr_tracking_status` enum: **`AWAITING_MERGE`** and
**`MERGE_ESCALATED`**.

```
OPEN ──(code-owner-approved & mergeable)──▶ AWAITING_MERGE ──(merge SLA breached)──▶ MERGE_ESCALATED
  │  clock held, chase code owner              │  clock running, chase owning team       │
  │                                            └──────────────(provider = merged)────────┴──▶ CLOSED
```

- Non-code-owner repos are unaffected — they never enter the two new states.
- Code-owner repos never enter the existing `ESCALATED` — their clock does not run in `OPEN`.

### Why `MERGE_ESCALATED` is a distinct status (not reusing `ESCALATED`)

`ESCALATED` is a state node whose outbound edges assume "approval is the finish line" — it has an
`approved + mergeable → CLOSED` exit. A PR in the merge phase is *already* approved + mergeable (that is
the entry condition), so routing a merge-phase breach into `ESCALATED` would close it on the very next
poll **without observing the merge** — re-introducing the exact close-on-mergeable bug this feature
removes. `MERGE_ESCALATED` carries merge-aware exits instead (close only on real merge), and shows up
distinctly in status/reporting. Escalating and *becoming* `ESCALATED` are separate concerns: we reuse
the escalation plumbing (`EscalationProcessingService`, target = owning team) as an effect, but persist
the dedicated status.

The "don't close on mergeable" guarantee is structural: neither new state has a `mergeable → CLOSED`
edge, so the only path to `CLOSED` is the top-priority "PR merged" row already in the table.

## Phased work

Each phase is sized to compile and test on its own.

### Phase 1 — Provider signals → `PrMetadata`
- `PrMetadata`: add `@Nullable ReviewDecision reviewDecision` (new enum) and
  `List<String> codeOwnerReviewers` (still-pending owners, for the message). Keep the existing
  back-compat convenience constructors defaulting them.
- **GitHub GraphQL client** — new client issuing the query above; map `reviewDecision` and collect
  `asCodeOwner == true` reviewers. Wire into `GitHubPrSourceClient.fetchPullRequest` /
  `Hub4jGitHubClient.getPullRequest`. Call it **only when `requires-codeowners`** (no cost otherwise).
  - New `RestClient` bean in `PrTrackingGitHubConfig` (base `…/graphql`). **Auth must mirror the
    configured `AuthMode`**: static token for TOKEN mode; an **installation token** for APP mode
    (`buildAppModeClient`). This is the one fiddly bit of the phase.
- **GitLab** — add the `approval_state` fetch in `GitLabPrSourceClient`; map the `code_owner` rule.
- Tests: JSON-fixture mapping tests for both clients.

### Phase 2 — Config
- Wire the existing `requires-codeowners` flag.
- **Drop the unused single-team `codeowner-team` field** — it cannot model per-folder ownership and
  `asCodeOwner` makes it redundant. (Mechanical churn to the convenience constructors.)
- Doc note: `requires-codeowners` assumes branch protection that requires code-owner review.
- Tests: config-bind test.

### Phase 3 — DB enum + migration
- New `Vnn__pr_tracking_merge_states.sql` mirroring the V13 lifecycle migration:

  ```sql
  alter type pr_tracking_status add value if not exists 'AWAITING_MERGE';
  alter type pr_tracking_status add value if not exists 'MERGE_ESCALATED';
  ```
- Regenerate the jOOQ enum: with the DB up (`make -C api db-up`), run `./gradlew :service:jooqCodegen`
  from `api/`. **Generated `com.coreeng.supportbot.dbschema.*` is not committed** — it is a build
  artifact regenerated by the build; do not add it to git.

### Phase 4 — FSM core (`PrLifecycle`) — pure, mock-free
- `Observation`: add `boolean requiresCodeowners`, `boolean codeownerApproved`.
- `SlaOp`: add `Start` (anchor `sla_deadline = now + sla`) for the deferred clock.
- `Effect`: add `NotifyAwaitingMerge` and `EscalateMerge`.
- `TRANSITIONS`: split the four `approved + mergeable → CLOSED` rows by `requiresCodeowners`
  (non-CO unchanged; CO → `AWAITING_MERGE` via `Start` + `NotifyAwaitingMerge`); add
  `AWAITING_MERGE → MERGE_ESCALATED` (slaBreached, `EscalateMerge`),
  `AWAITING_MERGE → CHANGES_REQUESTED` (pause), `MERGE_ESCALATED → CHANGES_REQUESTED`.
  Close-on-merged is inherited from the existing top-priority "PR merged" row.
- Regenerate the golden lifecycle diagram (`api/service/docs/diagrams/pr-lifecycle.generated.md`) via
  the diagram task / `-DupdateGolden=true`, and eyeball the new edges.
- Tests: a `decide()` unit case per new edge (no mocks).

### Phase 5 — Poller shell (`PrLifecyclePoller`) + repository
- `observe()`: populate `requiresCodeowners` (repo config) and `codeownerApproved`
  (`reviewDecision == APPROVED` / GitLab `code_owner` rule approved). Pass `repoConfig` through to
  `observe` (currently it isn't) so both are derivable.
- `apply()`: handle `SlaOp.Start` (set status + a fresh `now + sla` deadline). **Needs the configured
  SLA at poll time** — provisional source: the repo's configured default SLA (no merge clock when the
  repo has no SLA). The exact source ties into the deferred-clock open question.
- `runEffect()`: `NotifyAwaitingMerge` → render + post the new message event; `EscalateMerge` → mirror
  the existing `escalate()` but post the merge-specific message and write status `MERGE_ESCALATED`
  (one-shot `createEscalation`, target = owning team).
- **Repository / query layer (`PrTrackingRepository` + `JdbcPrTrackingRepository`) — required, easy to
  miss:** `findAllActive()`, `hasAnyActiveClosableForTicket()`, and `findAllInFlight()` currently
  enumerate only `OPEN/ESCALATED/CHANGES_REQUESTED/APPROVED`. They **must include `AWAITING_MERGE` and
  `MERGE_ESCALATED`** or a PR that reaches those states stops being polled and never closes. Also add a
  status-aware clock starter (e.g. `startSla(id, status, deadline)`) — `resumeSla` hard-codes `OPEN`,
  so it can't set `AWAITING_MERGE`.

### Phase 6 — Detection (`PrDetectionService`) — *touches the open question below*
- For `requires-codeowners` repos, insert the tracking record with **`sla_deadline = null`** (deferred
  clock); the clock starts later via `SlaOp.Start`.
- Code-owner-aware **detected** message listing `codeOwnerReviewers` (default text, or a per-repo
  override). Expose `codeOwnerReviewers` on the message context so overrides can reference them.

### Phase 7 — Messaging
- Add `AWAITING_MERGE` and `MERGE_ESCALATED` message events; add matching per-repo override fields and
  extend the renderer's compile loop. Default templates for the new events; provider-correct
  terminology.

### Phase 8 — Tests & verification
- Unit: Phase 4 `decide()` table; Phase 1 client mappings; Phase 2 bind; renderer.
- Integration (needs Docker — `make -C api db-up` then `./gradlew :service:build -Ddocker=true`):
  full code-owner lifecycle — detect (no deadline, chase message) → code owners approve + mergeable →
  `AWAITING_MERGE` (clock starts) → breach → `MERGE_ESCALATED` → merged → `CLOSED`.
- Lint gates run as **errors** in CI (Error Prone / spotless / checkstyle) — run `spotlessApply`
  before pushing.

**Suggested commit order:** 3 → 1 → 2 → 4 → 5 → 6 → 7, with tests folded into each.

## Open question

Whether `requires-codeowners` repos should sit in `OPEN` with **no live deadline** until
`AWAITING_MERGE` (the "deferred clock" assumed by Phase 6). Being clarified separately. If it changes,
only Phase 6 and the `OPEN`-row review in Phase 4 are affected — the rest of the plan stands.

## Risks / notes

- **GraphQL auth under APP mode** — the new client must obtain an installation token, not just read a
  static one (Phase 1).
- **`SlaOp.Start` needs the configured SLA at poll time**, which detection has but the poller does not
  yet fetch (Phase 5).
- **GitLab Code Owners is Premium/Ultimate** — validate or no-op `requires-codeowners` where the
  `approval_state` `code_owner` rule isn't available.
- **`reviewDecision` conflates** code-owner approval with any other required approvals — acceptable, as
  the gate we want is "all required reviews satisfied".
