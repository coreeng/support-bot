# Plan — functional-test coverage for PR-tracking config permutations

**Status:** ✅ Implemented — 19 functional tests added across 6 new classes; `spotlessCheck` +
`:testkit`/`:service`/`:functional` compile all green. (Full `:functional` suite runs in CI/Docker; the
sandbox has a known blanket-401 gap.) · **For:** PR #292 (`feat/pr-sla-code-owners`) · **Goal:** turn
every *applicable* cell of the flow × config coverage matrix green with a functional test.

The work is decomposed into a blocking **foundation** chunk plus six **independent** test-writing chunks,
each sized to hand to a single subagent. After the foundation lands, chunks 1–6 have no ordering
dependency on each other (each creates its **own** test class, so they never touch the same file).

---

## 1. Axes, flows, and the target matrix

**Config axes (8 permutations):** Provider (GitHub / GitLab) × Code owners (`requires-codeowners`
on/off) × SLA (configured / no-SLA paths-based).

**Flows:**

| # | Flow | State path |
|---|---|---|
| F1 | Happy path | `OPEN → approved+mergeable →` non-CO `CLOSED` · CO `AWAITING_MERGE → merged → CLOSED` |
| F2a | Review-SLA escalate → recover | `OPEN → (SLA breach) ESCALATED → approved+mergeable → CLOSED` |
| F2b | Merge-SLA escalate → merge | `AWAITING_MERGE → (merge-SLA breach) MERGE_ESCALATED → merged → CLOSED` |
| F3 | Changes-requested → approve → resolve | `OPEN → CHANGES_REQUESTED → approved+mergeable → resolve` |
| F4 | Abandoned | `OPEN → PR closed unmerged → CLOSED` |

**Applicability rules (why some cells are N/A, not gaps):**

- **F2a** needs a live deadline in `OPEN` **and** a non-CO repo (CO repos hold the clock in `OPEN`).
  → N/A for every CO column and every no-SLA column.
- **F2b** needs the merge clock, which only starts when the repo has an SLA, and only CO repos reach
  `AWAITING_MERGE`. → N/A for every non-CO column and every no-SLA column.
- **F3** requires observing a *changes-requested* review. **GitLab has no `CHANGES_REQUESTED` state**
  (`GitLabPrSourceClient.mapApprovals` is approve-only, by design). → **F3 is N/A for all GitLab
  columns.**

### Achieved matrix (✅ = functional test present; ➖ = N/A)

| Flow | GH·noCO·SLA | GH·CO·SLA | GH·noCO·noSLA | GH·CO·noSLA | GL·noCO·SLA | GL·CO·SLA | GL·noCO·noSLA | GL·CO·noSLA |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| F1 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F2a | ✅ | ➖ | ➖ | ➖ | ✅ | ➖ | ➖ | ➖ |
| F2b | ➖ | ✅ | ➖ | ➖ | ➖ | ✅ | ➖ | ➖ |
| F3 | ✅ | ✅ | ✅ | ✅ | ➖ | ➖ | ➖ | ➖ |
| F4 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

Every applicable cell green. **19 tests** filled the 19 gap cells (pre-existing ✅ cells were left as-is).

| Chunk | New test class | Cells (tests) |
|---|---|---|
| C1 | `PrCodeownerMergeGateFunctionalTests` | GH·CO·SLA F2b, F3, F4 (3) |
| C2 | `GitLabCodeownerLifecycleFunctionalTests` | GL·CO·SLA F1, F2b, F4 (3) |
| C3 | `GitLabLifecycleGapsFunctionalTests` | GL·noCO·SLA F2a, F4 (2) |
| C4 | `PrChangesRequestedLifecycleFunctionalTests` | GH·noCO·SLA F3 (1) |
| C5 | `PrNoSlaLifecycleFunctionalTests` | GH·{noCO,CO}·noSLA F1/F3/F4 (6) |
| C6 | `GitLabNoSlaLifecycleFunctionalTests` | GL·{noCO,CO}·noSLA F1/F4 (4) |

**Resolved — GitHub merged-vs-closed distinction.** Previously `stubGitHubGetPullRequest` never set
`merged_at`, so a GitHub `state:"closed"` stub mapped to `CLOSED`/`NOTIFY_CLOSED` and the "merged" and
"closed-unmerged" terminals were indistinguishable at the functional layer. Now the testkit has
`stubGitHubGetPullRequestMerged(...)` (sets `merged_at` → real `PrState.MERGED` → the `"PR merged"` FSM
row → `NOTIFY_MERGED`) and a body-matching `stubChatPostMessage(desc, channel, bodyContains)` overload.
The GitHub code-owner merged polls (C1 F2b/F3, C5 CO F1/F3) drive `NOTIFY_MERGED` and assert the
`"…merged…"` message; the abandoned polls (F4) assert the `"…closed…"` message — so the two terminals are
now genuinely distinguished, not just routed correctly. (GitLab already distinguished `merged`≠`closed`
via its state param; non-CO flows close via `NOTIFY_APPROVED`, not a provider merge.)

---

## 2. Mechanics every chunk relies on

- **Functional tests drive a running service on the `functionaltests` profile.** Per-repo *policy*
  (`requiresCodeowners`, SLA source, `owningTeam`, `paths`, messages) is resolved live from
  `application-functionaltests.yaml` by `PrLifecyclePoller.findRepoConfig(provider, repo)` on every
  poll — **not** from the seeded record. A repo that isn't in the config resolves to `null` →
  `requiresCodeowners=false`, no merge clock. **So a permutation cannot be tested until its repo exists
  in the config** (foundation, Chunk 0).
- **Seed-and-poll pattern** (see the "state machine transition tests" region of
  `PrLifecyclePollerFunctionalTests`): create a ticket, `createPrTrackingRecord(...)`, stub the
  provider API, `triggerPrTrackingPoll()`, assert the record's next `status`. Multi-step flows chain
  polls, re-stubbing the provider between them. Prefer this over detection-driven tests for lifecycle
  coverage — it's deterministic and bypasses path filtering.
- **Provider stubs (testkit `SlackWiremock`):**
  - GitHub REST PR: `stubGitHubGetPullRequest(desc, repo, n, state, createdAt, mergeable, reviewsJson)`
    — `reviewsJson` carries `APPROVED`/`CHANGES_REQUESTED` reviews.
  - GitHub code-owner gate: `stubGitHubGraphQlReviewDecision(desc, reviewDecision, pendingOwnerLogins)`
    — `reviewDecision` ∈ `APPROVED` / `CHANGES_REQUESTED` / `REVIEW_REQUIRED` / `null`.
  - GitLab MR: `stubGitLabGetMergeRequest(...)`, `stubGitLabGetMergeRequestApprovals(...)`.
  - GitLab code-owner gate: **`stubGitLabGetMergeRequestApprovalState(...)` does not exist yet** —
    added in Chunk 0 (client reads `GET …/merge_requests/:iid/approval_state`, a `code_owner` rule).
- **Verification environment.** The full `:functional` suite needs Docker + auth wiring that is **not
  available in this sandbox** (blanket 401s — env gap, not a regression). Each subagent's local bar is
  **compile + lint green**; the suite runs in CI / the documented Docker path.
  - Compile: `./gradlew :functional:compileTestJava :service:compileJava :testkit:compileJava`
  - Lint (CI treats these as **errors**): `./gradlew spotlessApply` then `spotlessCheck checkstyleMain`;
    Error Prone runs under `:service:build -Ddocker=true`.

---

## 3. Chunk 0 — Foundation (config + testkit) — **BLOCKING, do first**

**Goal:** add every missing repo and test-support primitive so chunks 1–6 are append-only and
deterministic. No new lifecycle tests here.

**Files:**
- `api/service/src/main/resources/application-functionaltests.yaml`
- `api/testkit/src/main/java/com/coreeng/supportbot/testkit/SlackWiremock.java`
- `api/testkit/src/main/java/com/coreeng/supportbot/testkit/SupportBotClient.java`
- `api/service/src/main/java/com/coreeng/supportbot/prtracking/rest/PrTrackingTestController.java`

**Tasks:**
1. **Add 5 repos** to `pr-review-tracking.repositories` (no-SLA repos require non-empty `paths`; use
   `paths: ["**"]` so any changed file matches):
   ```yaml
   - name: test-org/pr-nosla-repo            # GH·noCO·noSLA
     owning-team: wow
     paths: ["**"]
   - name: test-org/pr-codeowners-nosla-repo # GH·CO·noSLA
     owning-team: wow
     requires-codeowners: true
     paths: ["**"]
   - name: gitlab-org/gitlab-pr-codeowners-repo        # GL·CO·SLA
     owning-team: wow
     provider: gitlab
     gitlab-group-path: gitlab-org
     requires-codeowners: true
     sla: { default: PT24H }
   - name: gitlab-org/gitlab-pr-nosla-repo             # GL·noCO·noSLA
     owning-team: wow
     provider: gitlab
     gitlab-group-path: gitlab-org
     paths: ["**"]
   - name: gitlab-org/gitlab-pr-codeowners-nosla-repo  # GL·CO·noSLA
     owning-team: wow
     provider: gitlab
     gitlab-group-path: gitlab-org
     requires-codeowners: true
     paths: ["**"]
   ```
2. **Add `stubGitLabGetMergeRequestApprovalState`** to `SlackWiremock`, mirroring
   `stubGitLabGetMergeRequestApprovals` but on `…/merge_requests/:iid/approval_state`, returning a
   body with a `code_owner` rule:
   ```json
   {"rules":[{"rule_type":"code_owner","approved":<bool>,"eligible_approvers":[{"username":"<u>"}...]}]}
   ```
   Signature suggestion: `stubGitLabGetMergeRequestApprovalState(String desc, String repo, int iid,
   boolean codeOwnerApproved, List<String> eligibleApprovers)`.
3. **Let the seed API create records in a non-OPEN status** (needed to seed `AWAITING_MERGE` with a
   already-breached merge deadline for F2b, deterministically, without sleeps):
   - `SupportBotClient.PrTrackingToCreate`: add `@Nullable String status` (and `@Nullable Long
     escalationId` for MERGE_ESCALATED re-entry tests if convenient).
   - `PrTrackingTestController.createRecord`: after `insertIfAbsent`, if `status` is present and not
     `OPEN`, apply it via the existing `PrTrackingRepository.startSla(id, status, slaDeadline)` (which
     sets both status and deadline). Keep the default path (no status) unchanged.

**Acceptance:** `:service` + `:testkit` compile; `spotlessApply`/`checkstyle` clean; config binds
(`PrTrackingConfigValidationTest` still green). A one-line smoke assert that the new stub + seed-status
path work is welcome but optional.

---

## 4. Test-writing chunks (parallel after Chunk 0)

Each chunk creates its **own** JUnit class under
`api/functional/src/test/java/com/coreeng/supportbot/` (annotated `@ExtendWith(TestKitExtension.class)`,
`@BeforeEach cleanupPrTrackingRecords()`), so no two chunks edit the same file. Copy the seed-and-poll
scaffolding from `PrLifecyclePollerFunctionalTests`.

### Chunk 1 — GitHub code-owner gaps (GH·CO·SLA) — **HIGH PRIORITY**
Repo `test-org/pr-codeowners-repo` (already wired). New class `PrCodeownerMergeGateFunctionalTests`.
Cells: **F2b, F3, F4.** This is the headline feature (`MERGE_ESCALATED`) — currently zero functional
coverage, and every recent fix commit on the branch touches it.
- **F2b:** seed `status=AWAITING_MERGE`, `slaDeadline = now − 1h`. Poll 1: stub PR `open`+mergeable,
  GraphQL `reviewDecision=APPROVED`, merge-escalation `chat.postMessage`, rocket reaction on query →
  assert `MERGE_ESCALATED` + `escalationId` set. Poll 2: stub PR `closed`+merged → assert `CLOSED`,
  `escalationId` preserved. (Assert it does **not** close on mergeability in poll 1 — the core
  invariant.)
- **F3:** seed OPEN. Poll 1: REST reviews = `CHANGES_REQUESTED`, GraphQL `REVIEW_REQUIRED` → assert
  `CHANGES_REQUESTED`. Poll 2: REST `APPROVED`+mergeable, GraphQL `APPROVED` → assert `AWAITING_MERGE`.
  Poll 3: PR merged → `CLOSED`.
- **F4:** drive to (or seed) `AWAITING_MERGE`, then poll PR `closed` **not merged** → assert `CLOSED`
  with the *closed* (not merged) notice.

### Chunk 2 — GitLab code-owner (GL·CO·SLA) — **HIGH PRIORITY**
New repo `gitlab-org/gitlab-pr-codeowners-repo`. New class
`GitLabCodeownerLifecycleFunctionalTests`. Cells: **F1, F2b, F4** (F3 ➖). GitLab's code-owner gate is
entirely untested end-to-end. Uses `stubGitLabGetMergeRequestApprovalState` from Chunk 0.
- **F1:** seed OPEN. Poll 1: MR `opened`+mergeable, approvals stub, `approval_state` code_owner
  **approved** → assert `AWAITING_MERGE`. Poll 2: MR `merged` → `CLOSED`.
- **F2b:** seed `AWAITING_MERGE`, `slaDeadline = now − 1h`. Poll 1 → `MERGE_ESCALATED` + escalation.
  Poll 2 MR merged → `CLOSED`.
- **F4:** drive to `AWAITING_MERGE`, poll MR `closed` (not merged) → `CLOSED`.
- Watch: `approval_state` is fetched only while the MR is `OPEN` and only for CO repos — stub it on the
  open polls; the merged/closed polls skip it.

### Chunk 3 — GitLab non-CO gaps (GL·noCO·SLA)
Repo `gitlab-org/gitlab-pr-test-repo` (wired). New class `GitLabLifecycleGapsFunctionalTests`.
Cells: **F2a (upgrade 🟡→✅), F4** (F3 ➖).
- **F2a:** seed `slaDeadline = now − 1h`. Poll 1: MR open, no approvals → `ESCALATED` + escalation.
  Poll 2: MR mergeable + approvals present → `CLOSED`, `escalationId` preserved. (The existing GitLab
  escalation test stops at poll 1; this adds the recovery leg.)
- **F4:** seed OPEN, poll MR `closed`/`merged` terminal → assert `CLOSED` (there is currently no
  GitLab lifecycle-close test — the only merged-MR test is detection-time skip).

### Chunk 4 — GitHub non-CO changes-requested end-to-end (GH·noCO·SLA)
Repo `test-org/pr-test-repo` (wired). New class `PrChangesRequestedLifecycleFunctionalTests`.
Cell: **F3 (upgrade 🟡→✅)** — today the CR→approve→close path is only assembled piecemeal across three
tests and never reaches `CLOSED` in one run.
- Poll 1: REST `CHANGES_REQUESTED` → `CHANGES_REQUESTED` (SLA paused). Poll 2: REST `APPROVED`, not
  mergeable → `APPROVED`. Poll 3: mergeable → `CLOSED`. One method, one record.

### Chunk 5 — GitHub no-SLA lifecycle (GH·noCO·noSLA + GH·CO·noSLA)
New repos `test-org/pr-nosla-repo`, `test-org/pr-codeowners-nosla-repo`. New class
`PrNoSlaLifecycleFunctionalTests`. Cells: **F1, F3, F4 × both repos** (F2a/F2b ➖ — no deadline).
- Seed records with `slaDeadline = null`. Assert the no-SLA invariants explicitly:
  - non-CO: F1 `OPEN → approved+mergeable → CLOSED`; F3 CR→approve→close; F4 closed→CLOSED. Assert it
    **never escalates** (no live deadline).
  - CO: F1 `OPEN → AWAITING_MERGE → merged → CLOSED` and assert it **never reaches `MERGE_ESCALATED`**
    even when polled repeatedly (merge clock is a no-op with no SLA — the load-bearing
    `SlaOp.Start` no-op path). F3 CR→AWAITING_MERGE→merged; F4 closed→CLOSED.
- Watch: no-SLA repos apply a changed-file **path filter** (commit `4f238682`). `paths: ["**"]` matches
  everything; if a poll fetches files, stub `stubGitHubListPullRequestFiles` with any path.

### Chunk 6 — GitLab no-SLA lifecycle (GL·noCO·noSLA + GL·CO·noSLA)
New repos `gitlab-org/gitlab-pr-nosla-repo`, `gitlab-org/gitlab-pr-codeowners-nosla-repo`. New class
`GitLabNoSlaLifecycleFunctionalTests`. Cells: **F1, F4 × both repos** (F2a/F2b/F3 ➖).
- Mirror Chunk 5 on the GitLab adapter: non-CO F1 (approved+mergeable→CLOSED) + F4; CO F1
  (open→`AWAITING_MERGE` via `approval_state`→merged→CLOSED, never `MERGE_ESCALATED`) + F4.
- Same path-filter watch: `paths:["**"]`, stub `stubGitLabListChanges` if a poll fetches changes.

---

## 5. Dependency graph & suggested execution

```
Chunk 0 (foundation)  ──►  Chunk 1  (GH CO)          ┐
                      ──►  Chunk 2  (GL CO)          │
                      ──►  Chunk 3  (GL non-CO)      ├─ parallel; each owns a new test file
                      ──►  Chunk 4  (GH F3)          │
                      ──►  Chunk 5  (GH no-SLA)      │
                      ──►  Chunk 6  (GL no-SLA)      ┘
```

Chunks 3 and 4 use only pre-existing repos/stubs, so they *could* start before Chunk 0 lands — but
gating everything behind Chunk 0 keeps the config file single-writer and avoids conflicts.

**Priority tiers** (if not doing all at once):
- **Tier 1 (this PR's new surface, highest risk):** Chunk 1, Chunk 2.
- **Tier 2 (provider/flow parity):** Chunk 3, Chunk 4.
- **Tier 3 (no-SLA completeness, low marginal risk):** Chunk 5, Chunk 6.

## 6. Definition of done

- Every ● cell in the target matrix has a passing functional test (in CI / Docker env).
- New test classes compile and pass `spotlessCheck` + `checkstyleMain` locally; no Error Prone errors.
- CO tests assert the **negative** invariants (never close on mergeability; no-SLA CO never
  merge-escalates), not just the happy transitions.
- Update the coverage matrix in the PR description / this repo's docs to all-green + ➖.
