# doc-run

Orchestrates an **unattended, reviewed documentation run**. It wraps the `doc-journeys` skill — which consolidates scattered documentation into the consumer repository's documentation site — in a pipeline that plans, builds, reviews with independent agents, adversarially verifies the findings, fixes what survives, and hands back a branch ready to merge.

The defining property is that the run never stops to ask a question. Every decision the underlying skill would put to a human mid-run — confirming the plan, answering open questions, deciding whether a finding is applied — is resolved by the orchestrator using conservative defaults, and every one of those decisions is recorded in a close-out summary. **The human gate is the merge of the run's branch**, reviewed at the end, not a prompt in the middle.

---

## Invocation

```
/doc-tools:doc-run <documentation request>
```

The argument is a plain-language documentation request, passed to `doc-journeys` verbatim. It names one of:

  * **a product** — `/doc-tools:doc-run document Foglight`
  * **a journey under a product** — `/doc-tools:doc-run document the "Archive telemetry" journey for Foglight`
  * **a cross-product journey** — `/doc-tools:doc-run document the cross-product journey "Observe a service end to end"`

The request's wording also carries the intent, using the same keywords `doc-journeys` recognises: `refresh`, `force` / `regenerate anyway`, `extend` / `only what is missing`, `plan`. Where the target already has pages and the wording is ambiguous, the pipeline defaults to **refresh** — the safe reading — and says so in the close-out.

Two things it will not do:

  * A request resolving to `plan` mode ends after the plan is produced — nothing is written and no branch is created.
  * A request resolving to `audit` mode is rejected; audit writes outside this pipeline's writable locations, so invoke `doc-journeys` directly for that.

The target does **not** need to be declared already. A request naming a product or journey with no file under `product-definition/` is normal — the pipeline drafts the missing declaration, gates it, and writes it as part of the run. A product missing from `catalogue.md` still runs; the catalogue composes batches, it is not an admission list.

---

## Outputs

Each run works in its own git worktree and branch (`doc-run/<run-id>`, under the consumer's `worktree_dir`, off its `base_branch`), so several runs can proceed in parallel and each lands as one mergeable unit. Everything the run wrote goes in a single commit on that branch:

  * **Pages** — under the consumer's `output_root` (`products/`, `cross-product-journeys/`)
  * **Run report** — under `reports_dir`, a per-run diagnostic that renders beside the pages
  * **Sidecar proposals** — under `proposals_root`, for human-edited pages whose evidence changed (never published)
  * **Declarations** — new files under `product-definition/` where the request named something undeclared, plus at most a bounded `product.md` amendment and a one-line `catalogue.md` append

The run ends with a **close-out summary** in chat: the branch and merge command, the resolved intent (flagged where it was a default), every declaration reproduced verbatim with proposed-vs-found marking, every decision taken on the user's behalf, findings applied / handed to humans / overturned, and a direct preview link (the consumer's `preview_path` — a site's landing page may redirect elsewhere). The branch is not pushed and no PR is opened; the user reviews and merges locally.

---

## How it works

The orchestrator authors nothing itself. It spawns one **builder** agent that keeps its discovery context for the whole run, and a set of **reviewer** agents that are always fresh spawns — the builder's context contains its own rationalisations, and independent eyes are the point.

1. **Settings, worktree and branch** — read the consumer's `.doc-settings/settings.md` (stop if absent), then create `doc-run/<run-id>` from its `base_branch`, so the run is isolated and mergeable. Every value the pipeline uses is pinned from settings into every agent's spawn prompt.

2. **Resolve the request** — probe what already exists (declaration files, existing pages) and resolve the intent: author, refresh, force, or only-missing. A pre-run git baseline is captured so the builder's writes can later be told apart from anything pre-existing.

3. **Plan** — spawn `doc-tools:doc-builder`, which runs `doc-journeys` up to its confirm-before-writing gate and returns the resolved inputs, the full page plan with per-page dispositions and confidence, any proposed declaration, and any open questions. It writes nothing yet.

4. **Plan gate (automatic)** — the orchestrator reviews the plan for internal consistency (mode matches intent, existing pages respected, no journey silently dropped, declarations schema-valid) and confirms it. Open questions get the conservative default answer, recorded for the close-out. Rule overrides are honoured only when the user's original request stated them.

5. **Build** — the builder writes the confirmed declaration first, then pages, reports, and proposals, and returns a run manifest. The orchestrator cross-checks the manifest against `git status`: writes not in the manifest, or manifest entries not on disk, become findings; an unauthorised write under `product-definition/` stops the run.

6. **Structural gate** — spawn `doc-tools:doc-structure-reviewer`: mechanical checks only — a full site build verified at render level, title collisions, stubs, frontmatter sanity, template tag escaping, report self-consistency. It gates the expensive reviews: shipped-severity page defects go back to the builder for one fix round before anything else runs. Report-only defects do not gate; they are batched into the later fix loop while the deep reviews proceed in parallel.

7. **Deep review (parallel)** — three independent, read-only reviewers spawned together:
     * `doc-tools:doc-gap-auditor` — adversarially audits every claim of absence ("undocumented", "no prose found") by searching the whole source estate to refute it. Absence claims are the pipeline's historically weakest output.
     * `doc-tools:doc-entity-verifier` — corroborates every entity that lives outside the repositories (Slack channels, group handles, DLs, URLs, named people), since a citation cannot prove such a thing still exists.
     * `doc-tools:doc-routing-reviewer` — checks that pages route to existing documentation rather than restating it: reproduced commands, field lists, and self-contained duplicates that would drift silently.

8. **Verify, then triage** — findings that would change published claims (refuted gaps, entity removals, content deletions) go to `doc-tools:doc-finding-verifier`, which tries to **overturn** each one by independent re-derivation. Survivors are triaged: builder-fixable findings, findings only a human can act on (wrong source content, definition gaps — acknowledged in the report, never applied), and overturned findings (kept for transparency).

9. **Fix loop** — verified and mechanically-evidenced findings go to the builder, which applies them through the skill's own machinery (refresh rules, recomputed hashes) and records each under the report's `## Post-run corrections`. A delta-scoped structure re-run then spot-checks the fixes; one more round at most, after which anything still standing is named as unresolved rather than looped on.

10. **Commit and close out** — one commit on the run branch, then the close-out summary described under Outputs. Hard stops mid-pipeline produce the same commit-and-summary shape, opening with why the run stopped.

---

## Safety properties

  * All content writes happen in the run's worktree, never the main checkout; the pipeline inherits every `doc-journeys` guardrail (human-edited pages never overwritten, nothing deleted, source repositories never touched).
  * Under `product-definition/`, a run may **create** declarations (confirmed at the gate) and amend a `product.md` or append one catalogue line when the plan stated the exact diff. Briefs and existing journey declarations are never modified; the manifest cross-check enforces the bound and any breach stops the run.
  * Reviewer findings are treated as claims, not facts, until verified or mechanically evidenced — and a real finding whose fix would break a pipeline rule is still handed to a human rather than applied.

---

## Files

| File | Role |
| --- | --- |
| `SKILL.md` | The orchestration pipeline — steps, gates, agent contracts, recovery paths, and rules |

The agents the pipeline spawns ship in the same plugin and are addressed by their namespaced names: `doc-tools:doc-builder`, `doc-tools:doc-structure-reviewer`, `doc-tools:doc-gap-auditor`, `doc-tools:doc-entity-verifier`, `doc-tools:doc-routing-reviewer`, `doc-tools:doc-finding-verifier`.

The plugin is installed either from a local checkout for development — `claude --plugin-dir <path>/plugins/doc-tools` — or from the `coreeng` marketplace declared in the consumer's `.claude/settings.json` (`extraKnownMarketplaces` + `enabledPlugins`); see the plugin README.

---

## Running from a CI/CD pipeline

The pipeline is a natural fit for CI in one important way: it is already **unattended by design**. There is no interactive gate to script around — the run resolves every question itself and delivers a reviewable branch. Most of the machinery a scheduled "keep the docs current" job needs already exists:

  * **Change detection is built in.** Every generated page records the HEAD SHA of each source repo it cited (`repo_head`), and refresh mode asks git what changed since. `refresh where sources changed` is the cheapest useful invocation: a run where nothing moved reports one line and writes nothing, so a scheduled job that usually no-ops is cheap by construction. Discovery still re-runs in full, which is what catches *new* material in repos a page never cited — something a per-repo webhook diff alone would miss.
  * **Runs are isolated and mergeable.** The worktree-per-run model means a CI job produces one branch with one commit, exactly what a PR wants.

What a CI setup would need to provide:

  * **The full source estate on the runner.** The skill's source root is, by default, the parent directory of the consumer checkout, and discovery greps every sibling git repository. The job must clone all source repos side by side (full clones, not shallow — refresh compares recorded SHAs against history, and a shallow clone makes an unreachable SHA read as "evidence changed"). A nightly schedule with a mirror cache is more practical than cloning every repo per webhook.
  * **A headless agent runtime.** The orchestration is executed by Claude Code, not by a shell script — the job runs something like `claude -p "/doc-tools:doc-run refresh where sources changed"` with API credentials, the skills and agent definitions installed, the consumer's `.doc-settings/` checked out, and the site's dependency directory present at the consumer root for the build. Budget accordingly: a full run with reviews is hours of wall-clock and a nontrivial token spend, which is another argument for the scheduled-refresh invocation that no-ops when nothing changed.
  * **Delivery adapted to CI.** The current close-out deliberately does not push or open a PR — the user merges locally. A CI job would invert that: push `doc-run/<run-id>` and open a PR whose description is the close-out summary. The human gate stays exactly where the design puts it — on the merge — it just becomes PR review. The declaration-verbatim and decisions-taken material the close-out carries is precisely what the PR body should contain.
  * **Trigger and concurrency choices.** Per-push triggers across a large estate would stampede; a nightly or weekly schedule fits the change-detection model better. Runs should be serialised (or at least aware of each other): `catalogue.md` and shared section indexes are the known merge-conflict points between concurrent run branches.
  * **Scope control.** A batch refresh across every product is the obvious scheduled job, but per-product jobs (`refresh <product>`) keep diffs smaller and reviews shorter — and a failed product does not hold up the rest.

Two caveats worth stating plainly. Generation is non-deterministic, so even a well-behaved scheduled run produces prose-level diffs where evidence genuinely changed — the refresh rules keep those minimal, but PR reviewers should expect them. And the pipeline's protections assume merged output: pages merged with human edits are protected by `content_hash`, so the cycle only works if run branches are actually reviewed and merged rather than accumulating — stacked unmerged run branches would each re-derive the same changes against the same base.
