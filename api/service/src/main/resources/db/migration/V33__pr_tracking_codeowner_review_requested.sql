-- Sticky per-PR memory of whether a provider has EVER reported a genuinely pending code-owner
-- review request (GitHub reviewRequests asCodeOwner; see PrLifecyclePoller#observe). Provider APIs
-- don't retain this once the request is resolved (approved, dismissed, or invalidated by a later
-- push) or removed from the pending list, so a repo combining "require code-owner review" with a
-- separate minimum-approval-count rule reports the exact same signature (reviewDecision
-- REVIEW_REQUIRED, reviewRequests empty) whether code-owner review was never required for this PR's
-- paths, or was required and is now merely stale. The poller can't tell those apart from a single
-- poll's provider response, so it remembers the fact itself: set true the first time a pending
-- code-owner request is observed, and never unset.
alter table pr_tracking add column if not exists codeowner_review_requested boolean not null default false;

-- Backfill: records already past the code-owner gate had their pending window before this column
-- existed, and it can never recur (GitHub does not restore a dismissed reviewer to reviewRequests),
-- so leaving them false would hand them the "never applied" reading forever — a post-upgrade push
-- that dismisses a stale approval would then keep the merge clock running instead of returning the
-- record to OPEN. Marking them true preserves exact pre-upgrade semantics (trust the aggregate
-- decision): pre-V33 these states were only reachable via a genuine approved/inapplicable read, so
-- this can never wrongly hold the gate open.
update pr_tracking set codeowner_review_requested = true where status in ('AWAITING_MERGE', 'MERGE_ESCALATED');
