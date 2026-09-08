# doc-run

Orchestrates an **unattended, reviewed documentation run**. It wraps the
[`doc-journeys`](../doc-journeys/README.md) skill — which consolidates scattered documentation
into a consumer repository's documentation site — in a pipeline that plans, builds, reviews with
independent agents, adversarially verifies the findings, fixes what survives, and hands back a
branch ready to merge.

The defining property is that the run never stops to ask a question. Every decision the
underlying skill would put to a human mid-run — confirming the plan, answering open questions,
deciding whether a finding is applied — is resolved by the orchestrator using conservative
defaults, and every one of those decisions is recorded in a close-out summary. **The human gate
is the merge of the run's branch**, reviewed at the end, not a prompt in the middle.

Install both skills per the [skills README](../README.md): `doc-run` needs `doc-journeys`
installed beside it and stops if it is missing. Every example below uses **Foglight**, the
fictional observability product from
[`../doc-journeys/references/examples.md`](../doc-journeys/references/examples.md).

---

## Invocation

```
/doc-run <documentation request>
```

The argument is a plain-language documentation request, passed to `doc-journeys` verbatim. It
names one of:

  * **a product** — `/doc-run document Foglight`
  * **a journey under a product** — `/doc-run document the "Archive telemetry" journey for Foglight`
  * **a cross-product journey** — `/doc-run document the cross-product journey "Observe a service end to end"`

The wording also carries the intent, using the keywords `doc-journeys` recognises: `refresh`,
`force` / `regenerate anyway`, `extend` / `only what is missing`, `plan`. Where the target
already has pages and the wording is ambiguous, the pipeline defaults to **refresh** — the safe
reading — and says so in the close-out.

Two requests never reach the build:

  * one resolving to `plan` mode ends after the plan is produced — nothing is written and no
    branch is created
  * one resolving to `audit` mode is rejected, because audit writes outside the pipeline's
    writable locations; invoke `doc-journeys` directly for that

The target does **not** need to be declared already. A request naming a product or journey with
no file under `product-definition/` is normal — the pipeline drafts the missing declaration,
gates it, and writes it as part of the run. A product missing from `catalogue.md` still runs;
the catalogue composes batches, it is not an admission list.

---

## What the consumer provides

`doc-run` is consumer-agnostic in the same way `doc-journeys` is: everything about the
repository being documented comes from its `.doc-settings/` (see the
[consumer contract](../doc-journeys/README.md#the-consumer-contract) for the full picture). Of
the settings, the orchestrator itself uses:

| Key | Used for |
| --- | --- |
| `base_branch`, `worktree_dir` | where run worktrees branch from and are created; `worktree_dir` must be gitignored |
| `write_locations` | the `git status` scope for the pre-run baseline and the manifest cross-check |
| `output_root`, `reports_dir`, `proposals_root`, `plan_file`, `prior_art_roots`, `source_exclude_paths` | pinned into every agent's spawn prompt so no agent rediscovers or guesses a path |
| `build_command`, `build_toolchain`, `render_check` | the site build the structure reviewer runs, with `<consumer root>` substituted, wrapped in the toolchain shim where one is set |
| `preview_path` | the direct link reviewers are given to the output |
| `authorisations` | the file recording what an unattended run may do here — declare, amend, and so on — and who granted it; the close-out cites it |

Every agent is spawned with the **four path roles** pinned verbatim: the *tools root* (the
directory holding both skills, so a run always uses the pipeline as installed), the *consumer
root* (the main checkout — `.doc-settings/` and the site's gitignored dependency directory live
only here), the *repo root* (the run's worktree — every read and write of `product-definition/`,
pages, reports and proposals happens here) and the *source root*. Nothing in a run reads or
writes the main checkout's content tree.

Before the first run, work through the checklist in the starter's
[`assets/doc-settings/README.md`](../doc-journeys/assets/doc-settings/README.md#before-your-first-run).
The items that bite `doc-run` specifically: `worktree_dir` gitignored, `base_branch` existing,
and a `build_command` that points at the consumer root's dependency directory — a worktree has
none, and a build that only works from the main checkout fails the structural gate.

---

## What a run produces

Each run works in its own git worktree and branch (`doc-run/<run-id>`, under `worktree_dir`, off
`base_branch`), so several runs can proceed in parallel and each lands as one mergeable unit.
Everything the run wrote goes in a single commit on that branch:

  * **Pages** — under `output_root` (`products/`, `cross-product-journeys/`)
  * **Run report** — under `reports_dir`, a per-run diagnostic that renders beside the pages
  * **Sidecar proposals** — under `proposals_root`, for human-edited pages whose evidence changed
    (never published)
  * **Declarations** — new files under `product-definition/` where the request named something
    undeclared, plus at most a bounded `product.md` amendment and a one-line `catalogue.md`
    append

The run ends with a **close-out summary** in chat: the branch, worktree path and merge command;
the resolved intent, flagged where it was a default; every declaration reproduced verbatim with
proposed-vs-found marking; every decision taken on the user's behalf; findings applied, handed
to humans, and overturned; and the direct preview link. **The branch is not pushed and no pull
request is opened** — the user reviews the branch and merges locally
(`git merge doc-run/<run-id>` from `base_branch`), then removes the worktree.

---

## How it works

The orchestrator authors nothing itself. It spawns one **builder** agent that keeps its discovery
context for the whole run, and a set of **reviewer** agents that are always fresh spawns — the
builder's context contains its own rationalisations, and independent eyes are the point.

1. **Settings, worktree and branch** — read `.doc-settings/settings.md` from the consumer root
   (stop if absent or a required key is missing), check `worktree_dir` is gitignored, create
   `doc-run/<run-id>` from `base_branch`. Every value the pipeline uses is pinned into every
   spawn prompt from here on.

2. **Resolve the request** — probe what already exists (declaration files, existing pages) and
   resolve the intent: author, refresh, force, or only-missing. A pre-run `git status` baseline
   over `write_locations` is captured so the builder's writes can be told apart from anything
   pre-existing.

3. **Plan** — spawn `doc-builder`, which runs `doc-journeys` up to its confirm-before-writing
   gate and returns the resolved inputs, the full page plan with per-page dispositions and
   confidence, any proposed declaration, and any open questions. It writes nothing yet.

4. **Plan gate (automatic)** — the orchestrator reviews the plan for internal consistency (mode
   matches intent, existing pages respected, no journey silently dropped, declarations
   schema-valid) and confirms it. Open questions get the conservative default answer, recorded
   for the close-out. Rule overrides are honoured only when the user's original request stated
   them.

5. **Build** — the builder writes the confirmed declaration first, then pages, reports and
   proposals, and returns a run manifest. The orchestrator cross-checks the manifest against
   `git status`: writes not in the manifest, or manifest entries not on disk, become findings;
   an unauthorised write under `product-definition/` stops the run.

6. **Structural gate** — spawn `doc-structure-reviewer`: mechanical checks only — a full site
   build verified at render level, title collisions, stubs, frontmatter sanity, template tag
   escaping, report self-consistency. Shipped-severity page defects go back to the builder for
   one fix round before anything else runs; report-only defects do not gate and are batched
   into the later fix loop.

7. **Deep review (parallel)** — three independent, read-only reviewers spawned together:
     * `doc-gap-auditor` — adversarially audits every claim of absence ("undocumented", "no prose
       found") by searching the whole source estate to refute it. Absence claims are the
       pipeline's historically weakest output.
     * `doc-entity-verifier` — corroborates every entity that lives outside the repositories
       (chat channels, group handles, distribution lists, URLs, named people), since a citation
       cannot prove such a thing still exists.
     * `doc-routing-reviewer` — checks that pages route to existing documentation rather than
       restating it: reproduced commands, field lists, and self-contained duplicates that would
       drift silently.

8. **Verify, then triage** — findings that would change published claims go to
   `doc-finding-verifier`, which tries to **overturn** each one by independent re-derivation.
   Survivors are triaged: builder-fixable findings; findings only a human can act on (wrong
   source content, definition gaps — acknowledged in the report, never applied); and overturned
   findings, kept for transparency.

9. **Fix loop** — verified and mechanically-evidenced findings go to the builder, which applies
   them through the skill's own machinery (refresh rules, recomputed hashes) and records each
   in the report's corrections table. A delta-scoped structure re-run spot-checks the fixes; one
   more round at most, after which anything still standing is named as unresolved rather than
   looped on.

10. **Commit and close out** — one commit on the run branch, then the close-out summary. Hard
    stops mid-pipeline produce the same commit-and-summary shape, opening with why the run
    stopped.

The six agents are prompt files under [`agents/`](agents/), run as `general-purpose` subagents
with the run context appended: `doc-builder`, `doc-structure-reviewer`, `doc-gap-auditor`,
`doc-entity-verifier`, `doc-routing-reviewer`, `doc-finding-verifier`. Nothing has to be
installed beyond the two skills.

---

## What it never does

  * **Ask a question mid-run.** The only questions that reach the user are before the run (no
    request given) and after it (the close-out).
  * **Author, review or verify anything itself.** The orchestrator relays between agents; the
    builder never reviews its own work.
  * **Write to the main checkout's content tree.** All content writes happen in the run's
    worktree, and only under `write_locations`.
  * **Edit a source repository**, whatever a finding says. Those fixes are named for humans and
    stop there — the pipeline inherits every `doc-journeys` guardrail: nothing authored from
    scratch or from code, human-edited pages never overwritten, nothing deleted.
  * **Exceed the declaration bound.** Under `product-definition/` a run may create declarations
    confirmed at the gate, amend a `product.md` or append one catalogue line when the plan stated
    the exact diff — where the consumer's authorisations file permits it. Briefs and existing
    journey declarations are never modified; the manifest cross-check enforces the bound and any
    breach stops the run.
  * **Treat a finding as a fact.** Reviewer findings are claims until verified or mechanically
    evidenced, and a real finding whose fix would break a pipeline rule is handed to a human
    rather than applied.
  * **Push or open a pull request.** The human gate is the local merge.

---

## Files

| File | Role |
| --- | --- |
| [`SKILL.md`](SKILL.md) | The orchestration pipeline — steps, gates, agent contracts, recovery paths, and rules |
| [`agents/doc-*.md`](agents/) | The six agent prompt files, spawned as `general-purpose` subagents with the run context appended |

---

## Running from a CI/CD pipeline

The pipeline is already **unattended by design**, so there is no interactive gate to script
around. Two things it brings to a scheduled "keep the docs current" job:

  * **Change detection is built in.** Every generated page records the HEAD of each repository
    it cited, and refresh mode asks git what changed since. `/doc-run refresh where sources
    changed` is the cheapest useful invocation: a run where nothing moved reports one line and
    writes nothing. Discovery still re-runs in full, which is what catches *new* material in
    repositories a page never cited.
  * **Runs are isolated and mergeable.** One branch, one commit — exactly what a pull request
    wants.

What a CI setup has to provide:

  * **The full source estate on the runner.** Discovery scans every repository under the source
    root, so the job must clone them side by side — full clones, not shallow, because refresh
    compares recorded SHAs against history and an unreachable SHA reads as "evidence changed".
  * **A headless agent runtime.** The orchestration is executed by Claude Code, not a shell
    script: something like `claude -p "/doc-run refresh where sources changed"` with API
    credentials, both skills installed, `.doc-settings/` checked out, and the site's dependency
    directory present at the consumer root. A full run with reviews is hours of wall-clock and
    a nontrivial token spend.
  * **Delivery adapted to CI.** The close-out deliberately does not push or open a pull request.
    A CI job would add that step itself — push `doc-run/<run-id>` and open a pull request whose
    description is the close-out summary. The human gate stays on the merge; it just becomes
    review of the pull request.
  * **Trigger and concurrency choices.** A nightly or weekly schedule fits the change-detection
    model better than per-push triggers across a large estate. Serialise runs: `catalogue.md`
    and shared section indexes are the known merge-conflict points between concurrent run
    branches. Per-product jobs keep diffs smaller than one batch job over every product.

Two caveats. Generation is non-deterministic, so even a well-behaved scheduled run produces
prose-level diffs where evidence genuinely changed. And the protections assume merged output:
pages merged with human edits are protected by `content_hash`, so the cycle only works if run
branches are actually reviewed and merged rather than accumulating.
