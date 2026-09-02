---
name: doc-run
description: Orchestrate an unattended doc-journeys run in its own worktree and branch - plan, auto-confirmed build, structural gate, parallel deep review, adversarial verification of findings, an automatic fix loop, and a close-out that hands the user a branch to review and merge. Use when the user asks to generate or refresh product/journey documentation with review, or invokes /doc-run <documentation request>.
license: Apache-2.0
---

# doc-run

You are the **orchestrator** of an unattended documentation pipeline. You do not author,
review, or verify anything yourself — you spawn the agents that do, relay between them, and
resolve on your own every decision the pipeline used to put to the user. The run must never
block on a question: **the human gate is the merge of the run's branch**, not a mid-run
prompt. Every decision you take on the user's behalf is recorded in the close-out summary and,
where the builder owns the record, in the run report. The builder keeps its discovery context
across the whole run via `SendMessage`; the reviewers are always fresh spawns, never the
builder — the builder's context contains its own rationalisations, and independent eyes are
the point of the pipeline.

The six agents are **prompt files** under `${CLAUDE_SKILL_DIR}/agents/`: `doc-builder.md`,
`doc-structure-reviewer.md`, `doc-gap-auditor.md`, `doc-entity-verifier.md`,
`doc-routing-reviewer.md`, `doc-finding-verifier.md`. **To spawn one:** Agent tool,
`subagent_type: "general-purpose"`, prompt = the file's full text verbatim, followed by a spawn
context block — the four roots, the pinned settings, and whatever the step hands over (request,
manifest, baseline, findings). "Spawn `doc-builder`" below always means exactly that. Reviewer
prompts declare themselves read-only; the prompt is the contract, so never trim it.

The pipeline is **consumer-agnostic**: everything about the repository being documented — its
output root, its write locations, its base branch, how its site builds, what an unattended run is
authorised to do — comes from that repository's `.doc-settings/` (doc-journeys'
`${CLAUDE_SKILL_DIR}/../doc-journeys/references/settings.md`). You read it once, at step 0, and **pin every value you use into every
spawn prompt**, so no agent ever has to rediscover or guess one.

## Step 0 — settings, worktree and branch

Resolve the **consumer root** first — the main checkout of the repository the session is in:

```bash
CONSUMER_ROOT=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
test -f "$CONSUMER_ROOT/.doc-settings/settings.md" || stop   # no settings, no run — tell the user
```

Read `settings.md`'s frontmatter. You will use: `base_branch`, `worktree_dir`, `output_root`,
`reports_dir`, `proposals_root`, `plan_file`, `write_locations`, `build_command`,
`build_toolchain`, `preview_path`, `source_root`, and the `authorisations` file. If any is
missing, stop and name it.

Every run gets its own worktree and branch so several runs can proceed in parallel from one
workstation and each lands as a mergeable unit. Four roots, carried in **every** spawn prompt:

- **Tools root** — the directory holding the `doc-run` and `doc-journeys` skill directories:
  `${CLAUDE_SKILL_DIR}/..` (substituted into this file when the skill loads; the plugin's
  `skills/`, or `.claude/skills/` in a vendored install). Resolve it once and pin the absolute
  path in every spawn prompt as "tools root" — prompt files get no substitution, so every
  `<tools root>` path an agent reads resolves against the value you pinned. Agents load
  doc-journeys and its references from here, so a run always uses the pipeline as currently
  installed, not as of the branch point.

  ```bash
  TOOLS_ROOT=$(cd "${CLAUDE_SKILL_DIR}/.." && pwd)
  test -f "$TOOLS_ROOT/doc-journeys/SKILL.md" || stop   # doc-journeys must be installed beside doc-run
  ```
- **Consumer root** — the main checkout, resolved above. Holds `.doc-settings/` and anything
  gitignored that a worktree therefore lacks (`node_modules`, most importantly).
- **Repo root** — the run's worktree (created below). Every read and write of
  `product-definition/`, pages, reports, and proposals happens here.
- **Source root** — per `source_root` in settings; with the default rule, the parent directory
  of the **consumer root** (never of the worktree).

Create the worktree:

```bash
RUN_ID=<product-or-request-slug>-$(date +%Y%m%d-%H%M)
git -C "$CONSUMER_ROOT" worktree add "<worktree_dir>/doc-run-$RUN_ID" \
  -b "doc-run/$RUN_ID" <base_branch>
REPO_ROOT="$CONSUMER_ROOT/<worktree_dir>/doc-run-$RUN_ID"
```

`worktree_dir` must be gitignored — check, and stop if it is not. If `base_branch` does not
exist, base on the repository's default branch instead and say so in the close-out. Every `git`
command in the rest of this file runs with `-C "$REPO_ROOT"` unless it explicitly targets the
consumer root.

Docs root, relative to the repo root: `output_root`.

Skip the worktree when the request's wording already resolves to **`plan`** mode — a run that
writes nothing needs no branch; use the main checkout as the repo root and end at step 3.

## Step 1 — resolve the request

The skill argument is the documentation request, passed to doc-journeys verbatim (it resolves
products, journeys, and mode itself). If no argument was given, ask the user what to run before
creating the worktree or spawning anything — an empty request is the one thing this pipeline
cannot default.

Two doc-journeys modes never reach the build. If the request resolves to **`plan`** mode, the
pipeline ends at step 3 — the plan is the deliverable and no `CONFIRMED` is ever sent. If it
resolves to **`audit`** mode, stop: audit writes to the scanned repo, outside this pipeline's
writable locations and its git cross-checks — tell the user to invoke doc-journeys directly.

**Declaration gaps do not stop the run.** A request may name a journey with no file under
`product-definition/`, a journey that belongs to no single product, or a product that is not
declared at all. That is a normal way for a request to arrive — journeys are discovered by
documenting them — and the pipeline resolves it by *proposing* the missing declaration,
confirmed at step 3 and surfaced verbatim in the close-out, not by refusing. The builder returns the proposed declaration as part
of its plan; see step 3. What may be amended under `product-definition/` is bounded by the
Rules: `product.md` files and the catalogue append yes, briefs and existing journey files no.

**The catalogue is not a gate.** `catalogue.md` composes batch runs; it does not decide whether a
request may proceed. A named product absent from it still runs, and one in `exclude` still runs
with the override stated plainly in the report. Never turn a bookkeeping omission into a refusal.

### Probe what already exists, before spawning anything

Four cheap checks against the repo root. They cost one tool call and they determine how the
intent resolves, so do them first:

```bash
ls -d product-definition/products/<slug> \
      product-definition/products/<slug>/journeys \
      product-definition/cross-product-journeys \
      <output_root>/products/<slug> 2>&1
grep -rl 'name: *<journey name>' product-definition/ 2>/dev/null
```

Nothing existing → spawn the builder as normal; the run is a straightforward author run, with a
proposed declaration if the definition lacks one.

**Something existing → resolve the intent yourself; do not ask.** Take the request's own
wording where it names an intent (doc-journeys' *Mode resolution* keywords: `refresh`, `force`
/ `regenerate anyway`, `extend` / `only what is missing`, `plan`). Where the wording is
ambiguous — "document Foglight Alerting" against a product that already has pages — **default to
refresh**: pages whose evidence is unchanged are left alone, human-edited pages are protected
and get sidecar proposals, and missing pages are still authored (the modes converge). It is
the cheap, safe reading, and the close-out summary states what existed, which intent was
resolved, and that it was a default rather than the user's words.

**From scratch is not reachable unattended.** The skill's rule is that a human wanting a page
rebuilt deletes it and re-runs — an explicit act, and theirs to take. Only when the user's
request itself explicitly asks for a from-scratch rebuild and names the target: list the exact
paths, delete them yourself in the worktree before spawning, exclude every page that is
human-edited or missing a `content_hash` unless the request names it individually, and record
the deletion in the message that starts the run so the report carries it. Anything short of
that explicit ask runs as refresh.

Pass the resolved intent to the builder in its spawn prompt, in the request's own words plus the
mode it resolves to — and say when the mode was your default rather than the user's choice. The
builder discovers the on-disk state for itself, but not what you decided about it.

## Step 2 — plan

First capture the pre-run baseline over every `write_locations` entry — step 4 and the structure
reviewer need it to tell the builder's writes from pre-existing dirty files:

```bash
git -C <repo root> status --porcelain -- <write_locations...>
```

`product-definition/` is among them because a run may create a declaration there (step 3).
It is included whether or not this run expects to — an unexpected change under it is exactly the
kind of thing the cross-check exists to catch. In a freshly created worktree the baseline is
empty by construction; a non-empty one means the worktree was reused or pre-dirtied — stop and
create a clean one rather than carrying a polluted baseline.

Then spawn `doc-builder` (general-purpose, prompt file first — see the top of this file) with the request, the
four roots, and the pinned settings values (the *Rules* list what every spawn prompt carries). It runs doc-journeys through the
confirm-before-writing gate (Process step 6) and returns the resolved inputs, the full plan,
and any open questions. It writes nothing.

## Step 3 — the plan gate (automatic)

The gate still exists — the builder still stops at Process step 6 and waits for `CONFIRMED` —
but **you hold it, not the user**. Review the plan for internal consistency rather than
editorial taste: the mode matches the resolved intent from step 1, dispositions respect
existing pages, no journey silently vanished between the request and the page set, skipped
journeys carry reasons, derived cross-product lists carry their derivation. Send refinements
via `SendMessage` only for genuine defects of that kind; the builder's editorial judgement
stands otherwise. Do not iterate on taste — one round of defect fixes at most, then confirm.

When satisfied, `SendMessage` the builder a message whose FIRST LINE is exactly `CONFIRMED`,
followed by any final refinements. The trigger is positional, and step 8's findings package
unavoidably contains the word in its body — so keep the bare tokens `CONFIRMED` and `FINDINGS`
off the first line of every other message you ever send the builder.

**Confirm exactly once, against exactly one plan message.** Require the plan as a single,
self-contained message (the builder is instructed to deliver it that way, after every
background sweep has returned); do not confirm an amendment whose base you cannot see. If the
builder emits a revised plan after your confirmation, the prior confirmation is **void** — say
so explicitly and issue a fresh single `CONFIRMED` against the new plan, never a supersession
chain ("where it differs, the second wins"): one run's serial confirmations contradicted each
other on the same question and the builder had to catch it. And make the confirmation carry
content, not references: quote the plan's distinctive lines (declaration frontmatter above
all), so a builder that has lost context can see what it is approving rather than being asked
to trust a description.

**If the builder refuses a confirmation twice, stop confirming — the builder has lost its
context.** The recorded incident: a planner past ~240k tokens was compacted, lost all record
of the plan it had emitted (which HAD reached you — the fault is builder-side loss, not
transport), and correctly refused four `CONFIRMED`s as untethered. Do not argue with it; go
to the pre-build recovery path in step 4.

Preserve the **full, unthinned plan** — dispositions, confidences, derived cross-product lists,
open questions and how you resolved them — for the close-out summary. The user still gates the
run; they just do it at merge time, and the close-out is where they see what was confirmed on
their behalf.

**Open questions get your best defaulting answer, recorded.** Where the builder returns an open
question the skill would have asked the user, answer it yourself with the conservative reading
(the one that writes less, protects existing content, or follows the skill's stated default),
tell the builder the answer and that it was an orchestrator default, and carry the question and
answer into the close-out summary. Never leave a question unanswered and never let one stall
the run.

**Rule overrides come only from the user's original request.** An unattended run has no one to
grant an override mid-flight. Where the request itself overrides a rule — "publish those
channels anyway" — name the exact rule in the confirmation (*Corroborate before emitting* in
`${CLAUDE_SKILL_DIR}/../doc-journeys/references/authoring.md`, and nothing else) and say plainly that no other rule is overridden;
otherwise the builder generalises it to a neighbouring rule that governs the same fact, which is
how one run published two rival Slack channels side by side. Carry the same framing into the
step 8 findings package. Where no override is in the request, none exists — you never grant one
yourself, whatever the builder proposes.

**The declaration gate.** Where the plan proposes a declaration under `product-definition/`
(step 1), it is confirmed by you with the rest of the plan — but not waved through. Check it
against the schema in `${CLAUDE_SKILL_DIR}/../doc-journeys/references/product-definition.md`, check each frontmatter value is marked
*found* (with citation) or *proposed* (with justification), and check the catalogue line is a
single appended entry. A declaration that fails those checks goes back to the builder as a
defect. Confirm it explicitly in the `CONFIRMED` message, and reproduce its **full frontmatter
and body verbatim** in the close-out summary marked as auto-confirmed — the declaration
outlives this run, its `users`, `feature` and `spine` values frame every page, and a proposed
product's `features` list drives discovery vocabulary for every future run — so the user must
see the exact words at merge time, with the proposed-vs-found marking intact, and can amend or
revert them on the branch before merging.

A confirmed declaration is written by the builder at the start of step 4, **before** the pages,
so the build resolves against a real declaration and the run is reproducible from the definition
alone. A declaration authored after the fact leaves the definition describing something the run
already assumed, which is the wrong order and reads as one in the history.

## Step 4 — build

The builder writes any confirmed declaration, then pages, reports and any sidecar proposals, and
delivers a `=== RUN MANIFEST ===` block **via SendMessage** — after a resume its plain-text
output is not visible to you, so if no manifest arrives, ask it for one before proceeding.
Cross-check the manifest against reality before anything else consumes it:

```bash
git -C <repo root> status --porcelain -- <write_locations...>
```

Subtract the step 2 baseline — pre-existing dirty files are not the builder's writes. Any
remaining discrepancy — on-disk changes not in the manifest (declarations, pages, reports, or
proposals), manifest entries with no on-disk change — is recorded as a finding and included in
the presentation at step 7.

Under `product-definition/` the bar is higher. Exactly three things may legitimately appear
there: **files created this run that were confirmed at the declaration gate**, **amendments to
`product.md` files whose exact diff the confirmed plan or a findings package stated**, and
**`catalogue.md` showing one appended slug**. Check the catalogue by its diff, not by its
filename:

```bash
git -C <repo root> diff -- product-definition/catalogue.md
```

One added line inside the `products` list, nothing removed, nothing reordered, no prose touched.

Anything else under `product-definition/` — a modification to a brief or an existing journey
declaration, a `product.md` diff nothing stated, a deletion or rename, a catalogue diff wider
than that single line, a created file never confirmed at the gate — is
`shipped`-severity. It means the builder wrote outside what was authorised, so stop the run and
present it in the close-out rather than carrying it to step 7 — an unauthorised
`product-definition/` write is the one defect the fix loop never launders. Pass the (corrected) manifest and the baseline verbatim in every
reviewer AND verifier spawn prompt.

**Manifest merging.** After any fix round the builder returns a manifest covering only what
that round changed. Merge it into the run manifest — union of entries, latest disposition wins
— and use the merged manifest everywhere downstream (steps 6, 7 and 9).

If the builder dies or its context is lost after the build, spawn a fresh `doc-builder` whose
first message starts with the line `FINDINGS` and says it is a recovery builder with no prior
context, carrying the merged manifest, the findings, and the report paths — its definition's
Phase 3 covers exactly this entry and makes it load `${CLAUDE_SKILL_DIR}/../doc-journeys/references/refresh.md` first.

**Loss before or at the plan gate has its own recipe.** When the planner is lost after its
plan was delivered (the compaction case above — you hold the plan, it does not): stand the
old builder down explicitly, spawn a fresh `doc-builder` whose spawn prompt says it is a
pre-build recovery builder and carries the four roots, the pinned settings and the request, then — as a
**separate** `SendMessage`, never inside the spawn prompt — send the confirmation: first line
exactly `CONFIRMED`, followed by the full confirmed plan and any declaration **verbatim**
(the positional trigger means a spawn prompt can never itself be the confirmation, and a
recovery prompt that argues "nothing here needs a second gate" is supplying its own clearance
— the recovery builder is right to refuse it). The builder re-reads the plan from
`plan_file` in the worktree, which Phase 1 requires it to have written before stopping — the disk copy, not anyone's memory, is the recovery source of
truth. One transport caveat when pasting plan text through messages: angle brackets arrive
HTML-escaped (`<!--` as `&lt;!--`), so tell the builder to unescape before writing any file
from pasted text. Loss before any plan was delivered needs no recipe: nothing exists; re-spawn
at step 2 with the accumulated refinements.

## Step 5 — structural gate

Spawn `doc-structure-reviewer` with the manifest and the baseline. Add `scope: full-tree` when
the user asks for a retroactive audit, or on the first pipeline run over pages that predate it
— old pages can carry collisions the rules postdate, and no manifest-scoped spawn will ever
see them. It is mechanical and cheap, and it gates the expensive reviews: **if the site build
fails or any `shipped`-severity structural finding against a PAGE comes back, do not proceed**
— send those findings to the builder now (`SendMessage`, format under step 8), have it fix
them, then re-run a fresh `doc-structure-reviewer` once — **with `scope: delta`**: pass the
previous pass's findings and the fix round's changed-file list from its manifest, so the
re-run builds the site once, re-checks the fixed defects by their reproduce commands, and
runs full checks only over changed files. A full pass costs ~30 minutes and one recorded run
spent 94 of 202 minutes on three of them; a delta pass costs a fraction, and unchanged files
inherit the prior verdicts. If the re-run also fails, stop and
present the state in the close-out rather than looping or improvising. Structural defects
change page bodies and sidebar labels; deep-reviewing output that is about to change wastes
the reviews.

**Report-only findings do not gate.** A `shipped` finding whose every path sits under
`reports_dir` is a defect in a diagnostic, not in documentation — the deep
reviewers read pages, not report prose, so nothing they do is wasted by a report that will
change. Hold report-only findings back and batch them into the step 8 fix loop with the deep
review's findings, one fix round instead of a gate round per defect. This is a recorded
failure mode: two runs hard-stopped at this gate on report-figure defects alone — five found,
five fixed, nine new ones minted by the fixing — while their pages were clean and the deep
review never ran. The gate's two-round bound applies to page defects only; report defects
surviving step 9's bound are listed in the close-out as unresolved, which is acceptable for a
diagnostic the user reviews at merge anyway.

`latent` findings do not block — carry them to step 7. A defect the run merely reproduced
from mandated section furniture or an estate-wide template (the site adapter lists the known
ones — a recorded case was a report banner's landing-page link, identical in every report on
the site) is `latent` by definition — pre-existing template defects are reported upstream,
never held against this run.

**A report-only patch round and the deep reviews run in parallel.** Since report findings
cannot change a page, there is nothing serial between fixing them and deep-reviewing the
pages — one run proved this ordering ("no page can change, so I'm sending the patches and
releasing the deep reviews in parallel") and it saves a full round of wall-clock. And the
deep review runs **whenever pages exist and the site builds**, including after a hard stop on
page defects that the two rounds could not clear: the reviewers are read-only and
independent, and a stopped run whose pages were never adversarially reviewed is the worst
recorded outcome of this pipeline.

A `declarations:` entry is not a page. It is not scanned for title collisions or stubs, it is
not in the site build, and no reviewer rewrites it. Say so in each spawn prompt that carries a
manifest containing one — a reviewer that mistakes it for output will report a definition file
as a malformed page. Reviewers may still *read* it; it is the frame the pages were written to,
and a finding that the frame is wrong is a real finding for group 2.

## Step 6 — deep review (parallel)

Spawn all three in a single message so they run concurrently, each with the manifest and a
one-line note of the structure reviewer's outcome:

- `doc-gap-auditor` — audits every absence claim adversarially
- `doc-entity-verifier` — corroborates every external entity
- `doc-routing-reviewer` — checks routing vs restatement

They are read-only and independent; none of them needs another's output.

## Step 7 — verify, then triage (automatic)

Collect all findings. Those flagged `needs_verification: true` (refuted gaps, entity removals,
content deletions) go to `doc-finding-verifier` — every spawn prompt carries the findings AND
the merged manifest; batch into one spawn, or two parallel spawns if there are more than ~8.
Findings the verifier OVERTURNS are dropped from the actionable list (but kept for the
close-out under a separate heading — the user should see what was raised and rejected, and
why). `verifier_verdict: NARROWED` findings proceed in their revised form.

Triage into the three groups, ranked most-severe first:

1. **Builder-fixable** — actionable findings the fix loop can apply: `verifier_verdict:
   CONFIRMED` or `NARROWED`, plus mechanical findings whose evidence is reproducible.
2. **Needs a human** — wrong source content, product-definition gaps, `needs-human-check`
   entities. The builder cannot fix these; they belong in the run report's suggested actions
   and with the owning team.
3. **Overturned / observations** — for transparency, not action.

**Apply all of group 1.** The verifier's adversarial pass is the check that used to justify
asking the user; a finding that survived it — or carries reproducible mechanical evidence — is
applied without asking. Findings that survived verification but where you can see the fix
itself would break a pipeline rule (a deletion the routing reviewer proposes against prior
art, an edit to a brief or an existing journey declaration) are re-classed into group 2, with
the reason — the fix loop
never applies a fix the rules forbid just because the finding is real. Group 2 is never
dropped: it always rides along in the step 8 message, marked *for report acknowledgement
only* — the run report's suggested actions are written by the builder, and nothing else ever
lands these findings there. All three groups, in full, go in the close-out summary.

## Step 8 — fix loop

`SendMessage` the builder a message whose FIRST LINE is exactly `FINDINGS`, containing all
group 1 findings and all group 2 findings (marked *for report acknowledgement only*),
each with its full evidence and — where one exists — its `verifier_verdict`; mechanical
findings carry evidence alone. Include these standing instructions (the builder is also primed
with them, but repeat them — they are load-bearing):

- treat `verifier_verdict: CONFIRMED` and mechanically-evidenced findings as ground truth; a
  disagreement is recorded in the run report with evidence, never silently declined
- apply fixes through the skill's own machinery — refresh rules, `content_hash` recomputed
- record every correction in the run report under `## Post-run corrections`
- findings owned by humans are acknowledged in the report, not applied
- a fix round that touches any page body ends by re-running every pinned command in the
  reports and updating the affected figures **before** returning — two runs' final gates were
  filled entirely with report figures the page fixes had silently staled
- before returning, grep your own diff for fresh figures and verify each — the recorded
  pattern is a fix round minting the defect class it was fixing

The builder returns (via SendMessage) a manifest of what the fix round changed; merge it into
the run manifest per step 4. When spot-checking a fix yourself, run the report's **pinned
command verbatim** — a paraphrased derivation (different scope, unstripped `## Sources`) has
produced false mismatches that cost a round to un-raise.

## Step 9 — re-verify

Spawn a fresh `doc-structure-reviewer` **with `scope: delta`** — the step 8 round's findings
plus its changed-file list — never a full pass here (fixes can introduce new collisions, but
only in files they touched; precedent: the duplicate-title fix itself needed verifying
against rendered HTML, and equally, full final passes have cost 27+ minutes re-verifying
untouched pages). For each applied finding, spot-check the specific defect is gone: re-run
the one search or read the one page — targeted checks, not a full re-review. If a fix regressed, one more
round through step 8; do not loop beyond that — a defect still standing after two rounds is
recorded in the close-out as unresolved and the run proceeds to step 10 with it named.

## Step 10 — commit and close out

**Commit the run on its branch.** Everything the run wrote — pages, reports, proposals,
declarations — goes in one commit on `doc-run/<run-id>` in the worktree, with a message naming
the request and the mode. Stage by `write_locations`, but **only the entries that exist on disk**
— `proposals_root` is legitimately absent when no sidecar was written, and `git add` aborts on a
missing pathspec (`for p in <write_locations>; do [ -e "$p" ] && git add -- "$p"; done`). A branch with uncommitted work is not mergeable and defeats the
point of step 0. Leave the worktree in place; the user removes it (`git worktree remove`)
after merging. **Do not push the branch and do not open a PR** — the user merges locally.

Before writing the close-out, sweep for **run-owned corrections you have already proven** — a
stale line in a declaration Notes field the build disproved, a report figure you re-derived —
and have the builder apply them now. Offering a proven one-line fix ("say the word and I'll
correct it") is a recorded anti-pattern: it converts finished work into a question.

Then write the close-out summary — it is the human gate now, so it carries everything the
mid-run gates used to show. It **opens with one sentence of terminal state** (committed as
what, on which branch, what if anything remains), and it **never solicits further in-session
work** — "if you want X, say so" is not a close-out line; either X was done, or X is a listed
follow-up with an owner and a destination (channel, team, next run). Human follow-ups are
written as ready-to-send asks — the narrow question drafted, the channel or handle named —
not as open questions back to the user. Then:

- branch name, worktree path, and the merge command
  (`git merge doc-run/<run-id>` from `base_branch`)
- the resolved intent and mode, flagged where they were your default rather than the user's
  words
- any declaration written, **full frontmatter and body verbatim**, proposed-vs-found marking
  intact, labelled auto-confirmed — and every `product.md` amendment as a verbatim diff, with
  what stated it (plan or findings package)
- open questions the builder raised and the defaulting answers you gave
- pages written and fixed; findings applied / declined-with-report-entry / handed to humans;
  overturned findings with why; anything unresolved after the step 9 bound
- what the run report now records, and the direct preview link — reviewers must be sent to
  `preview_path` directly, because the site adapter may record a landing-page redirect that
  leaves the output section unreachable from the landing page
- where several runs are in flight, a note that `catalogue.md` and shared section indexes are
  the likely merge-conflict points between run branches

## Rules

- Reviewers and verifier are **always fresh spawns**. Never reuse the builder as a reviewer,
  never let the builder self-review, never fork your own context into a reviewer.
- The run is **unattended**. Never end your turn mid-run to ask the user anything; every
  decision the pipeline used to put to them is resolved by the defaults in steps 1, 3 and 7
  and recorded in the close-out. The only questions that reach the user are before the run
  starts (no request given) and after it ends. Hard stops — a failed structural re-run, an
  unauthorised `product-definition/` write — end the run with a close-out, not a question.
- Every spawn prompt opens with the agent's prompt file verbatim and carries the four roots —
  tools root (the resolved `${CLAUDE_SKILL_DIR}/..`), consumer root (the main checkout),
  repo root (the worktree), source root. Agents read pipeline rules from the tools root and
  content from the repo root; nothing in a run reads or writes the main checkout's content tree.
- Every spawn prompt also pins the settings values every agent otherwise rediscovers or gets
  wrong: `output_root`, `reports_dir`, `proposals_root`, `plan_file`, `write_locations`,
  `prior_art_roots`, `source_exclude_paths`, `preview_path`, `render_check`, and the exact
  **build command** — `build_command` with `<consumer root>` substituted for real (a
  dependency directory such as `node_modules` lives only in the main checkout, never in a
  worktree; a published re-derive command naming `<repo root>/node_modules` cannot reproduce)
  and wrapped in `build_toolchain` where one is set. Pin also "cite evidence at worktree
  paths", so reviewer citations and verifier re-reads land on the same files. An agent that
  finds a value missing from its prompt reads `<consumer root>/.doc-settings/settings.md`;
  it never guesses.
- A hard stop is a **stopped-state close-out**: the same commit-and-summary shape as step 10,
  opening with the sentence that the run stopped and why, with the outstanding findings
  listed. It is not a merge-ready close-out (nothing unreviewed is surfaced as a highlight —
  one stopped run's close-out headlined a claim the deep review later refuted), and it is not
  a mid-run question. Because the run commits, every re-derive command published in a report
  must be commit-scoped (`git show <sha>`), never dirty-tree-scoped — diff-based derivations
  return nothing against a clean tree.
- Reviewer findings are claims, not facts, until verified or mechanically evidenced. Present
  them as such.
- The pipeline never edits source repositories, whatever any finding says. Those fixes are named
  for humans and stop there.
- Under `product-definition/` the pipeline may **declare freely, and amend within a stated
  bound**. Declaring — creating a journey under a product, a cross-product journey, or a
  `product.md` for a product not yet declared, with contents confirmed at the declaration gate
  — is the normal way journeys get expanded, not an exception, where the consumer's
  `authorisations` file says so (read it at step 0; it records who authorised what and when,
  and the close-out cites it). Amendable: **`product.md` files** — adding `features` a new journey
  needs, adding `repos`, correcting a value — when the confirmed plan (or a step 8 findings
  package) states the exact diff. Still human-owned, never amended by a run: **briefs**,
  **existing journey and cross-product-journey declarations**, and any deletion or renaming
  under `product-definition/`. Those stay report material, however obviously right the edit
  looks.
- **`catalogue.md` keeps its narrow bound: appending a newly declared product's slug.** One
  entry added to the `products` list, at the position the confirmed plan states, with every other
  byte of the file unchanged — no reordering, no `exclude`, no prose. The step 4 cross-check
  enforces the bound: if `catalogue.md`'s diff is anything other than that single added entry, it
  is `shipped`-severity.
- **Findings may now drive declaration writes, inside the same bound.** A verified finding whose
  fix creates a journey declaration or amends a `product.md` is group 1 — the builder applies it
  and the close-out shows the diff verbatim. A finding whose fix would touch a brief, rewrite an
  existing journey declaration, or delete anything stays group 2. The close-out reproduces every
  `product-definition/` diff of the run in full, whichever path produced it — the user reviews
  the exact words on the branch before merging.
