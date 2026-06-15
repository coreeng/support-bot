# Spike: PR/MR Tracking Enhancements

**Status:** Draft (spike) · **Date:** 2026-06-15 · **Epic:** [PT-437](https://cecg-force.atlassian.net/browse/PT-437) — Support Bot GitLab + Multi Support Room

Related tickets:
- [PT-521](https://cecg-force.atlassian.net/browse/PT-521) — PR Tracking: MR **author verification**
- [PT-522](https://cecg-force.atlassian.net/browse/PT-522) — PR Tracking: **Dynamic approval rules**
- [PT-445](https://cecg-force.atlassian.net/browse/PT-445) — PR SLAs **code owners** (repos where code owners approve before the maintaining team merges)

This document captures a spike across three related enhancements to PR/MR lifecycle tracking. They are grouped because they share data-model prerequisites and all touch the same lifecycle state machine. The recommendation is to treat the shared groundwork as a single foundation, then layer the three features on top.

---

## 1. The three requirements

| # | Ticket | Requirement (short) | Where it bites |
|---|--------|---------------------|----------------|
| A | PT-521 | Only track PRs/MRs whose **author** belongs to a whitelisted team; ignore the rest. | Admission, *before* a tracking record is created. |
| B | PT-522 | Wait for **CI** to determine the **required** approvals, and only close once **all** required approvals are present (notify on partial). | The approval-evaluation guard inside the state machine. |
| C | PT-445 | Repos rely on **code owners** approving before the maintaining team can merge. Flag such repos; start the escalation timer **only after a codeowner approval**; override the *detected* message to tell the tenant to chase the codeowner; and close only on the **actual merge** (not on mergeable). | Admission message, the SLA/escalation clock, **and** the closure step. |

---

## 2. Current lifecycle (as built)

Source of truth: `PrLifecyclePoller` (`api/service/.../prtracking/PrLifecyclePoller.java`). Records are created by `PrDetectionService` in `OPEN`, then advanced on each poll (cron `0 0 9-18 * * 1-5`). Approval is reduced to a **single latest actionable team review** (`TeamReviewFilter.findLatestActionableReview`) — either `APPROVED` or `CHANGES_REQUESTED`. Mergeability is the tri-state `PrMetadata.isMergeable()`.

```mermaid
stateDiagram-v2
    [*] --> OPEN : PrDetectionService creates record (no author check)

    OPEN --> CHANGES_REQUESTED : team verdict = changes requested<br/>(SLA paused)
    OPEN --> APPROVED : approved & NOT mergeable<br/>(SLA paused, awaiting merge)
    OPEN --> CLOSED : approved & mergeable
    OPEN --> ESCALATED : SLA deadline breached<br/>(escalate to owning team)

    CHANGES_REQUESTED --> APPROVED : approved & NOT mergeable
    CHANGES_REQUESTED --> CLOSED : approved & mergeable
    CHANGES_REQUESTED --> OPEN : no actionable reviews remain<br/>(SLA resumed)

    APPROVED --> CLOSED : becomes mergeable
    APPROVED --> CHANGES_REQUESTED : changes requested<br/>(SLA stays paused)

    ESCALATED --> CLOSED : approved & mergeable
    ESCALATED --> APPROVED : approved & NOT mergeable
    ESCALATED --> CHANGES_REQUESTED : changes requested

    note right of CLOSED
        Also reached from ANY active state when the
        provider reports the PR/MR closed or MERGED
        (handlePrClosed). But the record is usually
        already CLOSED by then — see gap C.
    end note
```

### Properties / assumptions baked in today

1. **No admission gate.** Every detected PR/MR is tracked regardless of author → *gap A (PT-521)*.
2. **Single-approval model.** The *latest* actionable team review wins; there is no notion of "N required approvals", and CI state is ignored when deciding approval → *gap B (PT-522)*. (`mergeable` deliberately ignores required-check state, by design.)
3. **`approved + mergeable` is terminal, and there's no codeowner concept.** `handleApprovalClosure` sets `CLOSED` immediately — the bot never waits to observe the real merge → *gap C (PT-445)*. There's also no notion of a *codeowner* (distinct from the owning/approving team), no codeowner-gated SLA start, and the bot waits **silently** after approval rather than chasing the maintaining team to merge.

---

## 3. Target lifecycle (covering A + B + C)

Three changes, each isolated to one seam:

- **A — admission gate (PT-521):** before creating a record, resolve the author's team membership and skip tracking if they're not in the configured allowed-author team(s). This is upstream of the state machine (`PrDetectionService`).
- **B — approval predicate (PT-522), GitLab-only:** replace "latest actionable review" with `requiredApprovalsSatisfied(pr)` = *CI complete* **AND** *every required approval rule satisfied*, read natively from GitLab's `approval_state` (`report_approver` rules, which reflect policy escalation). While CI is still running on a dynamic-rules repo, the required set is unknown → stay `OPEN`/`AWAITING_APPROVALS`. A captured-but-incomplete approval emits a "1 of N" notification without closing. Enabled only for repos that actually use **MR Approval Policies** (GitLab Ultimate Tier Subscription) — see decisions below. **GitHub parity is out of scope** for this feature.
- **C — codeowners & merge gate (PT-445):** for a `requires-codeowners` repo: (1) at detection, override the *detected* message to tell the tenant they may need to chase the codeowner; (2) the escalation clock **starts only after a codeowner approval** (before that, the codeowner is chased, not the maintaining team); (3) once codeowner-approved and mergeable, do **not** close — enter `AWAITING_MERGE`, escalate/chase the maintaining (owning) team to merge, and close only when the provider reports `MERGED`.

```mermaid
stateDiagram-v2
    [*] --> AdmissionGate : PR/MR detected
    AdmissionGate --> NOT_TRACKED : author NOT in allowed team(s)  %% (A / PT-521)
    AdmissionGate --> OPEN : author allowed (or no whitelist)<br/>codeowner repo → override detected msg to chase codeowner  %% (C / PT-445)

    OPEN --> AWAITING_APPROVALS : dynamic-rules repo & CI not complete  %% (B / PT-522)
    AWAITING_APPROVALS --> OPEN : CI complete → required set known
    AWAITING_APPROVALS --> AWAITING_APPROVALS : partial approvals → notify "x of N"  %% (B)

    OPEN --> CHANGES_REQUESTED : changes requested (SLA paused)
    OPEN --> ESCALATED : SLA deadline breached (owning team)

    note right of OPEN
        (C / PT-445) codeowner repos: the escalation clock
        starts only AFTER a codeowner approval — before that
        the codeowner is chased, not the maintaining team.
    end note

    OPEN --> readyEval : requiredApprovalsSatisfied  %% (B): all required approvals present
    state readyEval <<choice>>
    readyEval --> CLOSED : mergeable & NOT requires-codeowners
    readyEval --> APPROVED : NOT mergeable (SLA paused, awaiting merge)
    readyEval --> AWAITING_MERGE : mergeable & requires-codeowners  %% (C): chase maintaining team to merge

    APPROVED --> readyEval : becomes mergeable
    APPROVED --> CHANGES_REQUESTED : changes requested

    AWAITING_MERGE --> CLOSED : provider reports MERGED  %% (C): close on ACTUAL merge
    AWAITING_MERGE --> AWAITING_MERGE : reminder / re-escalate maintaining team  %% (C, optional)
    AWAITING_MERGE --> CHANGES_REQUESTED : changes requested after approval

    CHANGES_REQUESTED --> readyEval : re-approved
    CHANGES_REQUESTED --> OPEN : no actionable reviews remain (SLA resumed)

    ESCALATED --> readyEval : re-approved
    ESCALATED --> CHANGES_REQUESTED : changes requested

    NOT_TRACKED --> [*]
```

> Diagram intent: new/changed elements are annotated with the owning area (A/B/C). Everything not annotated is existing behaviour. `readyEval` is a decision point, not a persisted status.

### What changes, concretely

- **New persisted statuses:** `AWAITING_MERGE` (C), and optionally `AWAITING_APPROVALS` (B) — DB enum `pr_tracking_status` extension, mirroring the V13 migration that added `APPROVED`/`CHANGES_REQUESTED`.
- **Codeowner concept** (C): distinguish a *codeowner* approval from an owning-team approval, so the bot knows whether to chase the codeowner or the maintaining team. Codeowners resolved via the CODEOWNERS file / requested teams / a configured codeowner team.
- **Codeowner-gated SLA start** (C): on `requires-codeowners` repos the escalation clock starts at the first codeowner approval, not at detection.
- **Detected-message override** (C): `requires-codeowners` repos get a tailored detected message (chase the codeowner) via the existing `PrMessageRenderer` override mechanism.
- **New escalation reason** (C): chasing the maintaining team to merge is distinct from SLA-breach escalation — same `ESCALATED` plumbing (`EscalationProcessingService`), different target/trigger.
- **Approval becomes a predicate** (B) rather than a single review lookup in `TeamReviewFilter`.
- **Closure waits for `MERGED`** on `requires-codeowners` repos (C) instead of `handleApprovalClosure` firing on `mergeable`.

---

## 4. Shared groundwork (do once, used by all three)

1. **Author identity on `PrMetadata`.** Today `PrMetadata` (and `GitHubPullRequest`) carry neither the author nor the merger. **A requires the author** (admission gate). **Merger identity is optional** — C closes on `MERGED` without checking who merged (see §7), so `merged_by`/`merge_user` is only worth capturing for richer "merged by X" messaging, not for any decision.
   - GitHub: author `user.login` (merger `merged_by.login` — optional).
   - GitLab: author `author.username` (merger `merge_user` — optional; `merged_by` is deprecated).
2. **Team/member resolution is reusable.** `TeamReviewFilter` → `resolveTeamMembers` already resolves GitHub team slugs and GitLab group paths (cached). A's allowed-author-team and C's codeowner team can reuse it; only new **config** fields are needed.
3. **Config shape.** New per-repo fields, reusing the existing `github-team-slug` / `gitlab-group-path` reference style:
   - A: `allowed-author-teams` (list)
   - C: `requires-codeowners` (flag) + optional codeowner team reference (else parse CODEOWNERS / use requested teams). Merge chasing targets the existing `owning-team`.
   - B: `dynamic-approvals` (flag) — "wait for CI before deciding required approvals"

---

## 5. Per-provider API feasibility (spike findings)

Verdicts from official-doc research (GitHub `docs.github.com`, GitLab `docs.gitlab.com`).

| Capability | GitHub | GitLab |
|---|---|---|
| **Author identity** (A) | `pull.user.login` — REST ✅ | `merge_request.author.username` — REST ✅ |
| **Author team membership** (A) | `GET /orgs/{org}/teams/{slug}/memberships/{user}` — REST ✅ (`read:org`) | `GET /groups/{id}/members/all` (incl. inherited) — REST ✅ (`read_api`, Reporter on group) |
| **Merger identity** (C) | `merged_by.login` — REST ✅ | `merge_user` — REST ✅ (`merged_by` deprecated) |
| **"Author can't self-merge"** (C) | **No single field.** Infer from branch protection `restrictions` (push/merge allow-list, **org/Enterprise only**) + CODEOWNERS parsing. → prefer **config-declared**. | **No single flag.** Read `protected_branches.merge_access_levels` (role/user/group); user/group-specific entries are **EE/Premium**. → prefer **config-declared**. |
| **CI completion** (B) | Combined status + Checks API; required checks via branch protection `required_status_checks` — REST ✅ | `merge_request.head_pipeline.status` — REST ✅ |
| **Required-approval state** (B) | No native "N required approvals" object; `mergeable_state = blocked` is a coarse, undocumented signal. Required reviewers are path-based only. | `GET .../merge_requests/{iid}/approval_state` exposes `report_approver` rules with `approvals_required`/`approved` — REST ✅ (**EE/Premium**) |
| **Dynamic "scan fails → require security team"** (B) | **Not native.** GitHub has no check-result-conditional reviewer rule; the bot itself must be the gate (required status check). | **Native** via MR **Approval Policies** (scan-result) — **Ultimate Tier Subscription** only; readable via `approval_state` (`report_approver` rules reflect the escalation). |

### Key cross-provider takeaways

- **A** is clean REST on both — lowest risk, smallest surface.
- **C** — "does this repo require codeowners / can the author merge?" is **not reliably queryable** on either provider → drive it from **config** (`requires-codeowners`), with optional CODEOWNERS-file / API enrichment to resolve codeowners. Closure is on the **actual merge** (`MERGED`); we do **not** verify the merger's team (see §7), so merger identity is optional.
- **B** diverges sharply: GitLab can express and **report** dynamic/policy approvals via the Approval Rules / Approval Policies API (Ultimate Tier Subscription); GitHub cannot express them natively. **Decision: GitLab-only, Ultimate Tier Subscription only** — see §6.1.

---

## 6. Recommended sequencing

1. **Foundation** — add the author to `PrMetadata` and both source clients (merger optional, messaging-only); add the config fields. Unblocks A and C.
2. **A (PT-521)** — author admission gate in `PrDetectionService`. Self-contained, clean REST, high value, low risk.
3. **C (PT-445)** — `requires-codeowners`: codeowner concept, codeowner-gated SLA start, detected-message override, new `AWAITING_MERGE` state, maintaining-team escalation, close-on-`MERGED`. Reuses the foundation; mostly lifecycle + messaging work, config-driven detection.
4. **B (PT-522)** — GitLab-only (see §6.1). Approval predicate + CI gate + partial-approval notifications, reading required state from `approval_state`. Tackle last / scope its own spike.

### 6.1 Decisions (area B)

- **No GitHub parity.** GitHub has **no native** check-result-conditional reviewer rule (rulesets support only *path-based* required teams). The only way to implement "if a scan fails, require the security team" on GitHub is for support-bot to *become the enforcement gate* — owning the policy logic and writing a required commit status. That inverts the bot's design: it **reads and reflects** provider state rather than enforcing merge policy, and there would be no provider-side source of truth to read the *required* approvals from. The complexity, fragility, and ownership shift aren't justified — so dynamic approvals are **GitLab-only**.
- **GitLab Ultimate Tier Subscription only, no degradation.** The feature is enabled **only** for repos that actually use **MR Approval Policies** (GitLab Ultimate Tier Subscription), where `approval_state` natively reports the policy-escalated `report_approver` rules. Rather than degrade gracefully on CE/Premium, the feature is simply **not offered** for those repos (config validation rejects / no-ops `dynamic-approvals` where policies aren't available). This keeps support entirely native — we read what GitLab computes, we never reimplement it.

### 6.2 Delivery strategy (branching)

**Do not** develop A, B and C on separate long-lived branches and merge them at the end. A is cleanly separable (it lives upstream in `PrDetectionService`), but **B and C both rewrite the same seam** — `PrLifecyclePoller`'s state machine and the `pr_tracking_status` enum migration — so parallel branches would collide in the same methods and in the migration. Instead:

1. **Foundation PR (to `main`).** Author on `PrMetadata` + both source clients, config plumbing, and any FSM/enum scaffolding shared by the features. Self-contained, low-risk, unblocks A and C.
2. **Stacked feature PRs, in order A → C → B.** Each is its own small PR rebased on the previous one — no long-lived parallel branches, no big-bang merge.
   - **A (PT-521)** — author admission gate. Independent, ships first.
   - **C (PT-445)** — codeowners + close-on-merge. Builds on the foundation; owns the FSM changes (`AWAITING_MERGE`, codeowner-gated SLA start, detected-message override).
   - **B (PT-522)** — dynamic approvals. **In scope but last** — the heaviest and most provider-divergent (GitLab Ultimate Tier Subscription only), and it should not gate A or C. Its FSM changes (`AWAITING_APPROVALS`) land *after* C's, avoiding the collision. A natural point to split into its own spike if scope grows.

This keeps the "one coherent effort on a shared foundation" benefit while leaving each piece independently reviewable and shippable.

---

## 7. Open questions

1. **Close-on-merge for all repos?** Should *every* repo switch from "close on mergeable" to "close on actual `MERGED`", or only `requires-codeowners` repos (to avoid changing existing behaviour)? Current default closes on mergeable.
2. **Re-escalation cadence** while in `AWAITING_MERGE` — one nudge, or recurring until merged?
3. **A — multiple teams & nested membership:** confirm whitelist semantics (any-of) and that GitLab inherited/invited-group membership counts.

> Resolved during this spike:
> - **C — escalation timer starts only after a codeowner approval (PT-445).** On `requires-codeowners` repos the escalation clock does **not** run at detection; it starts once a codeowner has approved (and then measures the maintaining team's time to merge). There is no silent "SLA paused" phase for these repos — the post-approval phase actively chases the maintaining team.
> - **C closes on merge, no merger check** — once the provider reports `MERGED`, that is sufficient to close. We do **not** verify that `merged_by`/`merge_user` belongs to the maintaining/codeowner team. (Consequence: merger identity capture is no longer required for C — see §4.)
> - **B has no GitHub parity** (see §6.1) — GitHub can't express conditional reviewers natively; making the bot the gate inverts its read-only design.
> - **B is GitLab Ultimate Tier Subscription only** (see §6.1) — offered only where MR Approval Policies exist, with no CE/Premium degradation path.
