---
name: Standing authorisations
description: The rulings under which unattended doc-run runs operate in this repository — who granted them, when, and what each one permits. Read by doc-run and doc-builder; cited wherever the skills say "standing authorisation".
granted_by: EDIT — a named person or role
granted_on: EDIT — YYYY-MM-DD
---

# Standing authorisations

Each entry is a consumer decision the skills would otherwise have to ask about mid-run. Where a
skill or agent says "standing authorisation", it means an entry here. The run's close-out cites
this file wherever it acted on one. `EDIT` every entry: strike what you do not grant, and the
skills will stop where a run needs it.

1. **Unattended runs.** `/doc-tools:doc-run` never asks the user anything mid-run. The
   orchestrator auto-confirms plans and declarations, applies every verified finding, and records
   every decision in the close-out. **The human gate is reviewing and merging the run's branch.**

2. **Worktree and branch per run.** Every run works in `<worktree_dir>/doc-run-<run-id>` on a
   branch `doc-run/<run-id>` off `base_branch`, so several runs can proceed in parallel from one
   workstation and each lands as one mergeable unit.

3. **This repository is a source.** Its existing documentation is consolidated — and where
   needed duplicated — into the output section. Only the pipeline's own output trees are
   excluded (`source_exclude_paths`). Contact-point corroboration still never counts this
   repository's prose.

4. **`product-definition/` may grow.** Runs may **create** journey declarations, cross-product
   journey declarations and `product.md` files for undeclared products — contents confirmed at
   the plan gate — and may **amend a `product.md`** (add `features`, add `repos`, correct a value)
   when the confirmed plan or a findings package states the exact diff. `catalogue.md` may gain
   **one appended slug** for a newly declared product. Briefs and existing journey declarations
   stay human-owned; nothing under `product-definition/` is ever deleted or renamed.

5. **No pushes and no PRs.** The run leaves its branch and worktree in place; the user merges
   locally with `git merge doc-run/<run-id>` from `base_branch`.

**Why:** `EDIT` — record the reasoning, so the next person to read this knows what problem each
grant solved and can revisit it. The usual one: hour-long runs stall on questions that cannot be
answered without knowing the content, and the report plus branch review is the accepted safety
net.
