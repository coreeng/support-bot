---
name: doc-journeys
description: Use when auditing a repository's markdown documentation against a defined set of user journeys to measure journey coverage, compare actual content weighting (by word count) against an ideal weighting table, and surface per-journey Diátaxis-type mix. Reads `product-definition/journeys/*.md` and `product-definition/weightings.md`. Supports a `coverage-only` mode that produces a truncated `doc-categorise-coverage.md` focused on coverage and weighting (Sections 1, 3, 4) only — no duplication, quality flags, suggested actions, or rewritten tree. For Diátaxis classification, duplication detection, hollow-page flagging, and stakeholder action lists without journey-weighting analysis, prefer the `doc-categorise` skill instead.
---

# doc-journeys

## When to use
Use this when the user asks to "measure journey coverage", "compare doc weighting", "weighting analysis", "journey audit", "journey gap analysis", or mentions `coverage-only` / "up to Section 4" / "journey relevance summary", **and** a `product-definition/journeys/` folder (and optionally `product-definition/weightings.md`) is present in the target repo. This skill is the journey-and-weighting-focused variant of `doc-categorise`; if the user just asks to "categorise", "audit", "Diátaxis-ify", or "find documentation gaps" with no mention of journeys or weighting, prefer `doc-categorise`. Outputs are configurable via the execution mode: `full` produces both the rewritten tree and a report; `report-only` produces the full report only; `coverage-only` produces a truncated coverage-and-weighting report only. In every mode, originals are never edited, moved, renamed, or deleted.

## Documentation model

The skill operates on a small, explicit model of what good documentation is. Every rule in this skill maps to one of these principles or to a deliberately-scoped quality-of-life feature. The skill does NOT enforce anything outside this model.

**Principles the skill enforces:**

1. **User-focused** — every documentation page targets an intended User (a persona). The skill detects audience via content signals and journey-supplied user labels, and reports drift between authored intent and how the page actually reads. (Operationalised in `references/audience-tagging.md`.)

2. **Two audience tiers**:
   - **Product-level documentation** (reference, explanation, how-to) aimed at the product's builders/maintainers — anyone who extends, operates, or repairs the product.
   - **Journey-level documentation** (end-to-end how-to) aimed at end-users — the people who consume the product through its supported journeys.

3. **Journey-level coverage** — every supplied journey should have at least one matching how-to. Missing how-tos are the **only** "high"-severity actions the skill emits. (Operationalised in `references/gap-analysis.md` Part A.)

4. **Single-type focus** — each piece of documentation should target exactly one of the four Diátaxis types (tutorial, how-to, reference, explanation). Pages that straddle types are classified as REWRITE (drift toward a dominant intent) or SPLIT (multiple distinct intents).

**Things the skill explicitly does NOT assert:**

- That every product must carry all of reference + explanation + how-to. Product-level coverage (Subsection B of Coverage analysis) is reported descriptively as counts; the reader judges per-product whether the spread is right.
- That documentation must follow any particular style guide. Style consistency is gated on a style guide being explicitly defined; deferred.
- Factual correctness. "Incorrect bits" require an oracle of correctness; out of scope.

**Quality-of-life features** (reported alongside the principles above but not derived from them):

- **Duplication candidates** — structural clustering by `(journey, type, variation)` tuple. See `references/duplication.md`.
- **Quality flags** — deterministic `hollow` (stub) and `stale-marker` (explicit `deprecated`/`TODO`) checks. See `references/quality-flags.md`.

These are general documentation-hygiene checks; the skill does not claim they are part of any principle.

## Reference materials

This skill ships with reference files under `references/` that hold the classification logic. They are load-bearing — you MUST consult them rather than reinvent the logic from this file.

Load order:

- `references/product-definition.md` — load **at the very start of every run, before anything else**. Specifies how the skill ingests the product name and journey list, the schema for the `product-definition/` folder, and the paste fallback. Resolves the blocking input step.
- `references/compass.md` — load **at the start of classification, once per run**. Two-question decision tool that resolves most pages on its own.
- `references/types.md` — load **at the start of classification, once per run**. Signals, anti-signals, voice tells, drift modes, and boundary-case disambiguation for each of the four Diátaxis types. Required to determine PERFECT vs NOT-PERFECT.
- `references/decision-rubric.md` — load **on demand**, when the compass is ambiguous, content is mixed, or no type fits. Fixed-procedure rubric covering SPLIT, OUTLIER, low-confidence, and user-escalation cases.
- `references/journey-matching.md` — load **after classification completes, once per run, only if `journeys` is non-empty**. Specifies the two-pass hybrid procedure (deterministic + LLM) that tags each scanned page with journey relevance (name, variation, confidence).
- `references/audience-tagging.md` — load **after journey matching completes, once per run, always**. Specifies how to assign an audience tier (builder/maintainer vs end-user) and detailed audience labels to each scanned page, plus the mismatch-detection procedure.
- `references/gap-analysis.md` — load **after audience tagging completes, once per run, always**. Specifies per-journey coverage verdicts (covered/partial/missing) and descriptive product-level page counts by Diátaxis type. Produces the Coverage analysis section of REPORT.md.
- `references/weightings.md` — load **after gap analysis completes, once per run, only if `product-definition/weightings.md` was resolved**. Specifies the optional ideal-vs-actual weighting comparison: schema for the weightings input, word-counting and apportionment rules, and the extended Part A structure (A1/A2/A3) in the Journey relevance summary. Skipped silently when the input is absent or unparseable.
- `references/topic-coverage.md` — load **after weighting analysis completes, once per run, only if `journeys` is non-empty**. Specifies the LLM-based topic-extraction and present/missing matching procedure that produces Part A4 of the Journey relevance summary. Non-deterministic. Runs in all execution modes including `coverage-only`.
- `references/duplication.md` — load **after gap analysis completes, once per run, always**. Specifies the intentionally minimal duplication-candidate rule: structural grouping by `(journey, Diátaxis type, variation)`, no LLM judgement. Read its "What this does NOT catch" section before interpreting the cluster output.
- `references/quality-flags.md` — load **after duplication detection completes, once per run, always**. Specifies the two deterministic quality flags (`hollow` and `stale-marker`), with no LLM judgement. Read its "What this does NOT catch" section before interpreting the output.
- `references/suggested-actions.md` — load **after quality flags complete, once per run, always**. Specifies the deterministic synthesis that turns prior-step outputs into a single prioritised list of recommended actions, using a fixed enum of nine action types and three severity tiers.
- `references/examples.md` — load **on demand**, when you need a worked exemplar to pattern-match against. Optional.

Do not invent disambiguation logic. If you reach a case the rubric does not cover, escalate to the user using the scoring format the rubric specifies.

## Required input (blocking step)

Before performing any scanning, classification, or rewriting, you MUST resolve two values:

- `product_name` — a single string identifying the product the documentation belongs to.
- `journeys` — a list of journey records (possibly empty) representing what users do with the product.

Both are resolved per `references/product-definition.md` — load that file at the very start of every run. The reference file is authoritative on schema, parsing, validation, and paste-fallback wording; the steps below are the procedural skeleton only.

### Step 1 — Read the `product-definition/` folder

Look for `<repo_root>/product-definition/` at the root of the repository being scanned.

If present and `product-definition/product.md` is valid (has a non-empty `name`):
- Parse `product.md` per `references/product-definition.md` → `product_name`, owners, features.
- Parse every `*.md` file under `product-definition/journeys/`. Skip files missing the required `name` field and record the skips.
- Print the resolved product (name, owners, features) and the list of journey names (plus any skipped files) back to the user. Confirm before scanning.

If `product-definition/` is missing, or `product.md` is missing/invalid, fall through to Step 2 — the paste fallback for both `product_name` and `journeys`.

If `product-definition/` exists and `product.md` is valid but `journeys/` is missing or empty, ask the user only for the journey paste (Step 2B), keeping the folder-resolved `product_name`.

### Step 2 — Paste fallback

Ask the questions verbatim as specified in `references/product-definition.md`:

- **2A** — "What product is this documentation for?" (skipped if `product_name` is already resolved from the folder).
- **2B** — "Paste your list of journeys (one per line, or markdown bullets). Type `not applicable` if there are no journeys."

Apply the normalisation, parsing, and `not applicable` semantics specified in `references/product-definition.md`. Pasted input is held in memory only; never written to disk.

### Step 3 — Strict validation

Per `references/product-definition.md`:
- `product_name` must be non-empty after trimming, single-line, and free of markdown formatting characters.
- Reject and re-ask on violation.

### Step 4 — Execution gating

You MUST NOT proceed to:
- repo scanning
- classification
- rewriting
- file generation
- reporting

until both `product_name` and `journeys` are resolved. `journeys = []` is a valid resolution and the skill MUST proceed in that case.

### Step 5 — Frontmatter injection rules

Every output file MUST include the following frontmatter block. This rule applies to all outputs from all verdicts — every PERFECT copy, every REWRITE output, **and every individual file produced by a SPLIT (no matter how many)**. The only file not subject to this rule is `REPORT.md` itself.

---
product: "<product_name>"
diataxis_type: "<tutorial|how-to|reference|explanation>"
source_path: "<original path>"
---

Field semantics:
- `product` — the resolved `product_name` from Step 1 or 2A. Identical across every output of a single run.
- `diataxis_type` — the type assigned to this specific output. May differ between sibling outputs of a SPLIT.
- `source_path` — the source file's path relative to the repo root. For SPLIT outputs, this is the path of the source page (the same value appears on every sibling output of a SPLIT).

If journey matching produced at least one match for the source page (per `references/journey-matching.md`), an additional `journeys:` field is appended to the frontmatter — see that reference file for the schema. Omit the field entirely when the match list is empty; do not emit `journeys: []`.

An `audience:` block is **always** appended to the frontmatter (per `references/audience-tagging.md`), regardless of journey-match status. It carries `tier` (the binary `builder/maintainer` vs `end-user` used by this skill), `labels` (free-form list), `confidence`, `source`, and an optional `mismatch` block when journey-supplied audience disagrees with content-inferred audience. See the reference file for the schema.

Feature data resolved from `product-definition/` is **not** injected into output frontmatter as a standalone field. Downstream consumers join back to the input journey list via the journey `name` to recover feature context.

## Execution mode

The skill has three execution modes:

- **`full` (default)** — produces the Diátaxis-organised tree under the output root **and** writes REPORT.md inside it. Full behaviour described elsewhere in this file.
- **`report-only`** — produces REPORT.md only. All analysis steps (classification, journey matching, audience tagging, gap analysis, duplication, quality flags, suggested actions) still run, and the placement map is still computed so the report can describe where each page would land. The tree is **not** written: no PERFECT copies, REWRITE outputs, SPLIT outputs, assets, or directories are created.
- **`coverage-only`** — truncated report focused on coverage and weighting. Implies no tree (like `report-only`) and additionally skips the duplication, quality-flags, suggested-actions, and placement-map steps. The report includes the header, TL;DR, Summary (trimmed), Coverage analysis, and Journey relevance summary only; Section 2 (Suggested actions), Section 5 (Duplication), Section 6 (Quality flags), the engineer block, and the wrap-up are all omitted. Output file: `<repo_root>/doc-categorise-coverage.md`.

### Mode resolution (blocking)

The mode is resolved at the start of every run, alongside `product_name` and `journeys`. It is **blocking** — the skill MUST NOT proceed to output root resolution, scanning, classification, or any other step until `mode` is resolved.

1. If the user's invocation contains any case-insensitive match of `coverage only`, `coverage-only`, `up to section 4`, `coverage focus`, or `coverage report` → set `mode = coverage-only`. Do not ask. (Checked first because some triggers contain the substring `report`.)
2. If the user's invocation contains any case-insensitive match of `report only`, `report-only`, `no tree`, `skip rewrites`, or `analysis only` → set `mode = report-only`. Do not ask.
3. If the invocation contains any case-insensitive match of `full mode`, `with tree`, or `full run` → set `mode = full`. Do not ask.
4. Otherwise, **ASK the user explicitly** with this exact phrasing:

> "Run mode: **full** (writes the rewritten/categorised tree under `docs/` or `docs.proposed/`, AND a `REPORT.md` inside it), **report-only** (writes only `<repo_root>/doc-categorise-report.md` — no tree, no rewrites), or **coverage-only** (writes only `<repo_root>/doc-categorise-coverage.md` — truncated report ending at Section 4, no tree, no duplication/quality/actions analysis)? Reply 'full', 'report only', or 'coverage only'."

Accept any answer that matches the trigger phrases above. If the user replies with something ambiguous (e.g. "yes", "idk", "default"), repeat the question — do not pick for them and do not silently default.

The mode is per-run; it is not persisted.

### `report-only` mode specifics

- The single output is `<repo_root>/doc-categorise-report.md`. The output-root resolution rules below do **not** apply.
- The skill does not create any directories.
- Process steps "write generated files" and "copy assets" are skipped.
- The report's structure is identical to a `full`-mode report. "Output path" columns in PERFECT/REWRITE/SPLIT tables describe the path the file *would have been at* in `full` mode (the placement map is computed but not materialised on disk).

This mode is a deliberate temporary affordance for stakeholder demos where the rewritten tree adds noise. It may be removed in a future version of the skill once the default tree-producing behaviour is the more common choice.

### `coverage-only` mode specifics

- The single output is `<repo_root>/doc-categorise-coverage.md`. The output-root resolution rules below do **not** apply. The filename is distinct from `doc-categorise-report.md` so the two modes can coexist without overwriting each other.
- The skill does not create any directories.
- Process steps 9 (duplication), 10 (quality flags), 11 (suggested actions), 12 (placement map), and 13 (write files) are **all skipped**. Only steps 1–8 and 14–15 run. **Step 8 (topic coverage) runs** because Part A4 is part of the Journey relevance summary.
- The report omits Section 2 (Suggested actions), Section 5 (Duplication candidates), Section 6 (Quality flags), the `## Detail for reviewers` divider, the entire engineer block, and the wrap-up. See the Executive report format section for the exact set of sections retained.
- The Summary table is trimmed: the `Asset count`, `Unresolved-link count`, and `Suggested actions` lines are omitted (the underlying values are not computed). All other Summary lines are unchanged.
- The TL;DR's Sentence 3 (`The doc set has {S} hollow stubs and {M} pages with stale markers.`) and Sentence 4 (`{K} duplicate cluster(s) identified.`) are never emitted in this mode because their inputs are not computed; Sentences 1 and 2 are emitted as normal.

This mode is for stakeholder demos focused on coverage, weighting, and topic gaps, where the duplication / quality / actions detail would be noise.

## Output root resolution

Applies only in `full` mode. In `report-only` and `coverage-only` modes, see "Execution mode" above for the single-file output location.

1. Locate the repo's existing docs folder by checking these names in order: `docs/`, `Docs/`, `documentation/`, `Documentation/`. The first match wins.
2. If a match is found → output root is `<repo_root>/<match>.proposed/` (e.g. `docs.proposed/`, `documentation.proposed/`).
3. If no match is found → output root is `<repo_root>/docs/`.
4. Always create these subfolders under the chosen root: `tutorials/`, `how-to/`, `reference/`, `explanation/`, `assets/`.
5. The executive report is written to `<output_root>/REPORT.md`.

## Source scope (user-supplied at invocation)
- Required input: repo root path.
- Optional inputs: include globs, exclude globs. Defaults if user gives none: include `**/*.md`; exclude `node_modules/**`, `vendor/**`, `.git/**`, `**/CHANGELOG.md`, `**/LICENSE.md`, the chosen output root itself, and `product-definition/**`.
- The `product-definition/` folder at the repo root is **always** excluded from the documentation scan, regardless of include globs supplied by the user. Journey files are inputs to the skill, not documentation to classify.
- Confirm the resolved file list with the user before writing anything if it exceeds ~50 files.

## Classification

For each source page, assign exactly one of four verdicts:

- **PERFECT-<type>** — the page is unambiguously one Diátaxis type with no drift. It clearly exhibits the type's characteristic signals (per `references/types.md`; not necessarily every signal listed) **and contains zero anti-signals for that type**. A single off-type table, an explanatory digression, or a tutorial flag enumeration disqualifies it — each is named in the type's anti-signal list. Action: copy into the matching subfolder verbatim. "Verbatim" means content-verbatim: the Step 5 frontmatter and the provenance comment are still injected, and any existing frontmatter is preserved-and-merged with the required fields. Mechanical link and asset path rewrites are also allowed.
- **REWRITE-<type>** — the page has a single dominant intent but drifts in tone, structure, or off-type content. Action: rewrite into one file in the dominant category per the rewrite rules below. Preserve every fact, code block, command, and example; reword prose and reorder sections only as needed to fit the type's voice and structure.
- **SPLIT-<types>** — the source page produces multiple output files. Triggered when the page (a) blends two or more Diataxis types (any non-dominant section ≥ ~25% of the page, or any code/step block that belongs to another type), and/or (b) contains multiple independent units — different goals, subjects, or scopes — that each warrant their own page. There is no cap on output count: a README covering ten independent topics may produce ten files. **Each output is strictly single-type.** Action: identify unit boundaries per `references/decision-rubric.md`; output one file per unit; cross-link siblings with a "See also" footer in each file; record the split in the report.
- **OUTLIER** — the page does not fit any Diátaxis type (release notes, meeting minutes, FAQs that are not Q&A reference, scaffolding, etc.). Action: do not place under any category; list in the report under "Outliers" and skip.

Procedure for each page:

1. Apply `references/compass.md`.
2. Verify the candidate type against `references/types.md` signals and anti-signals.
3. If the compass returns one type, no anti-signals fire, and no off-type signals are present → **PERFECT-<type>**.
4. If the compass returns one type but anti-signals or off-type signals are present → **REWRITE-<type>**.
5. If the compass is ambiguous, content is mixed, or no type fits → load `references/decision-rubric.md` and follow its procedure to reach **SPLIT-<types>** or **OUTLIER**.

## Rewrite rules (no-meaning-change)
- Never invent, drop, or contradict facts, commands, code, parameter names, version numbers, links, or warnings.
- Reword prose for the type's voice (imperative for how-to, lesson voice for tutorial, declarative for reference, discursive for explanation).
- Reshape structure (headings, ordering, lists vs tables) to fit the type.
- Preserve all code fences, command examples, and admonitions verbatim.
- Preserve existing frontmatter; add `product: <product_name>`, `diataxis_type: <category>`, and `source_path: <original repo-relative path>` per Step 5 of the Required input block. Do not change existing `title`/`description` unless empty.
- Prepend a single HTML comment at the top of every generated file (including PERFECT copies): `<!-- Generated by doc-categorise from <source_path>. Do not edit; edit the source. -->`

## Naming and collisions

Filename rules by verdict:

- **PERFECT** and **REWRITE** outputs → `<output_root>/<category>/<source_basename>.md`. Example: source `docs/foo.md` placed as `docs/how-to/foo.md`.
- **SPLIT** outputs → `<output_root>/<category>/<slug>.md`, where `<slug>` is derived from the unit:
  - For a unit that maps to one source heading → slug the heading directly. `## Install on Linux` → `install-on-linux.md`.
  - For a grouped unit (variants of one goal sharing a generated title) → slug the generated title with any leading Diátaxis-type prefix word ("How to", "About", "Why", "Understanding") and any product-name token stripped. `How to install Foglight` → `install.md`. `About Foglight's sampling model` → `sampling-model.md`.
  - If a unit has no natural heading (rare; e.g. the only how-to portion of an otherwise single-type source), fall back to the source basename.

Slug rules: lowercase, dash-separated, ASCII only. Strip punctuation other than dashes.

Collision resolution: if two outputs would share the same `<output_root>/<category>/<filename>.md`, append a path-derived slug suffix from the source path. `foo.md` from `folder-a/onboarding/foo.md` becomes `foo--folder-a-onboarding.md`. Cross-type outputs (different category folders) cannot collide because they live in different folders.

## Links and assets
- Inter-doc markdown links (`[text](path.md)` or `[text](path.md#anchor)`):
    - If the target is also being placed under the output tree → rewrite to the new `<output_root>/<category>/<file>.md` location (relative to the current file).
    - If the target is outside the categorized set → leave as-is and flag in the report under "Unresolved links".
- Image and asset references (`![alt](path)` or HTML `<img src=...>`): copy the asset into `<output_root>/assets/`, dedup by content hash, rename on collision with a path-derived slug. Update the reference in the generated file to point at the copied asset.
- External `http(s)://` links are left untouched.

## Process
1. Resolve `product_name`, `journeys`, and execution `mode` per the Required input block and the Execution mode section. All three are blocking. Confirm the resolved values with the user before continuing.
2. Resolve output root and source file list; print both back to the user before writing.
3. For each source file: extract frontmatter, headings, and a content sample; classify per the Classification section (applying `references/compass.md` and `references/types.md`, escalating to `references/decision-rubric.md` for ambiguous cases) as PERFECT-<type>, REWRITE-<type>, SPLIT-<types>, or OUTLIER.
4. If `journeys` is non-empty, tag each source file with journey relevance per `references/journey-matching.md`. Skipped entirely when `journeys = []`.
5. Tag each source file with audience per `references/audience-tagging.md`. Always runs. Uses journey-relevance results from step 4 when available; falls back to content inference when no journey match exists or the matched journey has no `users:` field.
6. Compute coverage gaps per `references/gap-analysis.md`. Always runs. Produces a per-journey coverage verdict (covered/partial/missing) — Part A skipped when `journeys = []` — and descriptive product-level page counts at the builder/maintainer audience tier (Part B always).
7. Compute weighting actuals per `references/weightings.md`. Runs only when `product-definition/weightings.md` was resolved and parsed; otherwise skipped silently. Produces per-journey and per-type-within-journey actual percentages from per-page word counts and journey matches, for inclusion in the extended Part A.
8. Compute topic coverage per `references/topic-coverage.md`. Runs only when `journeys` is non-empty; otherwise skipped. Two LLM passes per journey: Pass 1 extracts a canonical topic list from journey description prose; Pass 2 judges present/missing per (topic, matched-page) batch. Produces Part A4 of the Journey relevance summary. Non-deterministic.
9. Identify duplication candidates per `references/duplication.md`. Always runs (section appears in REPORT.md either way); pages without strong journey matches are not analysed.
10. Apply quality flags per `references/quality-flags.md`. Always runs. Two deterministic checks: `hollow` (mostly empty page) and `stale-marker` (explicit deprecation/TODO keywords).
11. Synthesise suggested actions per `references/suggested-actions.md`. Always runs. Walks the outputs of steps 3–7 and 9–10 and emits one action per matching signal from a fixed enum of nine action types and three severity tiers. **Topic-coverage results (step 8) are NOT inputs to suggested actions** — the action enum is fixed and topic gaps are descriptive only.
12. Build the global placement map (paths + collision resolution). Always runs — the placement map is needed for the REPORT.md tables even in `report-only` mode.
13. In `full` mode: write generated files, copy assets, rewrite links using the placement map. **Skipped in `report-only` mode.**
14. Write the report. In `full` mode: `<output_root>/REPORT.md`. In `report-only` mode: `<repo_root>/doc-categorise-report.md`. In `coverage-only` mode: `<repo_root>/doc-categorise-coverage.md`.
15. Print a one-screen summary in chat with counts and a pointer to the report.

**Mode-conditional skipping:**
- In `report-only` mode, step 13 (write files / copy assets) is skipped.
- In `coverage-only` mode, steps 9 (duplication), 10 (quality flags), 11 (suggested actions), 12 (placement map), and 13 (write files) are all skipped; only steps 1–8 and 14–15 run. Step 8 (topic coverage) **runs** in `coverage-only` because Part A4 is part of the Journey relevance summary.

## Executive report format (`REPORT.md`)

REPORT.md is organised in two reader-priority blocks separated by a divider. The **exec block** carries actions, gaps, and signal summaries — what an executive reader needs. The **engineer block** carries per-page classification detail behind a `## Detail for reviewers` H2 divider that lets exec readers stop at a clear boundary.

### Cross-section consistency invariants (read before generating any section)

The per-page journey-relevance and audience tags emitted in REPORT.md's Sections 8 (Copied verbatim) and 9 (Rewritten) are the **ground truth** for every aggregated count elsewhere in the report. The following invariants MUST hold across the whole report. They are not aspirational — they are required, and the agent MUST self-check every aggregated count against the per-page tags before emitting the report.

1. **Journey coverage table (Section 3 Subsection A)** — for every journey row, `strong_how_to_count` and `weak_how_to_count` MUST equal the count of pages in Sections 8/9 tagged with that journey at the corresponding confidence, restricted to `how-to` Diátaxis verdicts. See `references/gap-analysis.md` Step 4b for the explicit self-check.
2. **Per-journey page count table (Section 4 Part A)** — for every journey row, the per-Diátaxis-type cell counts MUST equal the count of pages in Sections 8/9 tagged with that journey at any confidence, broken down by Diátaxis type. The parenthetical `(N weak)` annotation MUST equal the count restricted to weak matches.
3. **Duplication clusters (Section 5)** — every cluster MUST consist of pages tagged with the cluster's journey at confidence `strong` in Sections 8/9, with matching Diátaxis type and variation.
4. **Suggested actions (Section 2)** — every action MUST correspond 1:1 with a triggering signal as defined in `references/suggested-actions.md`. The "Top 3 risks" list MUST be the first three rows of the same action table.
5. **Weighting actuals (Section 4 Parts A2 and A3, when present)** — every actual % MUST be derivable from per-page word counts and per-page journey-match tags. Word counts MUST be computed once per page and reused; aggregates MUST NOT recompute word counts on a sub-selection or re-evaluate which journeys a page matches. See `references/weightings.md` for the apportionment formula.
6. **Topic coverage (Section 4 Part A4) is explicitly OUT of scope for these invariants.** Topic lists are LLM-extracted and present/missing verdicts are LLM-judged; neither is derivable from per-page tags. Other report sections MUST NOT cite Part A4 as ground truth for any aggregated count or verdict.
7. **Per-journey page index (Section 4 Part A5)** — every row MUST be derivable from per-page journey-match tags. The set of `(journey, page)` pairs in A5 MUST equal the union of all per-page journey matches at any confidence. The Diátaxis-type cell MUST equal the page's classification verdict from step 3. No re-judgement, no filtering, no demotion of weak matches. The row count per journey, restricted to pages tagged at the corresponding confidence, MUST equal the corresponding strong/weak counts in Sections 8/9.

**No re-judgement.** The journey-matching, audience-tagging, and classification steps already decided which pages match which journeys, what audience they read for, and what Diátaxis type they are. Later aggregations count and cross-reference; they do NOT re-evaluate. If the agent is tempted to filter or downgrade a per-page tag while building an aggregated count, that's a violation of these invariants — re-run the relevant judgement step instead.

If an invariant cannot be satisfied, the per-page tags are the source of truth — re-derive the aggregate, do not adjust the per-page tags.

### Header and conventions preamble

Every REPORT.md begins with, in this order:

1. An H1 title: `# doc-categorise report — <product_name>`
2. Bold-labelled metadata lines: `**Run mode:**`, `**Product:**`, `**Journeys:**`, `**Scope:**`.
3. **A conventions blockquote**, included verbatim whenever `journeys` was non-empty for the run. It defines the `strong` and `weak` terms used throughout the report so readers do not have to consult reference files:

> **Journey-match confidence:** each page-to-journey match is `strong` (the page covers the journey substantively or end-to-end) or `weak` (the page touches on the journey but only covers part of it — e.g. one step, one variation, or as an aside). Only strong matches participate in duplication clustering.

4. **A `## TL;DR` section** (only when `journeys` is non-empty), 3–5 sentences, derived deterministically from prior-step outputs. Sentences are emitted in the fixed order below; sentences 2–4 are each conditional on the underlying counts being non-zero. Total length is always 3–5 sentences after conditional filtering.

   - **Sentence 1 (always):** `Of {N} journeys, {X}/{N} are covered ({P}%); {Y} are missing; {Z} are partial.`
     Where N = total supplied journeys, X = count with verdict `covered`, Y = count with verdict `missing`, Z = count with verdict `partial`, P = round(X/N × 100).
   - **Sentence 2 (only if Y > 0):** `Missing: {comma-separated names of journeys with verdict 'missing'}.`
   - **Sentence 3 (only if S + M > 0):** `The doc set has {S} hollow stubs and {M} pages with stale markers.`
     Where S = pages flagged `hollow`, M = pages flagged `stale-marker`. If only one is non-zero, mention only the non-zero count.
   - **Sentence 4 (only if K > 0):** `{K} duplicate cluster(s) identified.`
     Where K = count of clusters from `references/duplication.md`.

   No editorial expansion. No impact narrative. The TL;DR is mechanical — counts derived from the data, plain sentences.

5. A horizontal rule (`---`).
6. Section 1 (Summary) follows.

When `journeys = []`, omit both the conventions blockquote and the TL;DR — the terms and stats they reference do not exist.

### Exec block

In `coverage-only` mode, the **Suggested actions**, **Duplication candidates**, and **Quality flags** sections below are omitted entirely (the underlying steps are skipped); the **Summary** table is trimmed as described in its bullet.

- **Summary**: a single concise metric table. The metrics it MUST contain — and the only metrics it may contain — are:
  - Total source files scanned.
  - Counts per Diátaxis category (tutorial / how-to / reference / explanation).
  - Counts of PERFECT / REWRITTEN / SPLIT / OUTLIER.
  - Asset count. *(Omitted in `coverage-only` mode — not computed.)*
  - Unresolved-link count. *(Omitted in `coverage-only` mode — not computed.)*
  - Audience tier counts, exact format: `end-user X, builder/maintainer Y`.
  - Suggested actions count, exact format: `Suggested actions: N (high X · medium Y · low Z)`. *(Omitted in `coverage-only` mode — not computed.)*
  - **Only when `mode = report-only`**: a projected-output line with exact wording: `Output files that would be written in 'full' mode (excludes outliers): ~N. This run is 'report-only'; no files were actually written.` The wording is **mandatory** — do not abbreviate to "Output files in full mode" or similar, because that phrase is opaque to readers who haven't read the header metadata.
  - **Only when `mode = coverage-only`**: a mode-explainer line with exact wording: `Mode: coverage-only — Sections 2, 5, 6, the engineer block, and the wrap-up are omitted. Re-run with mode 'report-only' or 'full' for the full picture.`

  Do **not** repeat the output root or the run mode in the table — both are already in the header metadata above. The skill MUST NOT add metrics beyond those listed above. If a new metric becomes useful, it belongs in a new section, not appended to the Summary.
- **Suggested actions** (omitted in `coverage-only` mode; otherwise always): deterministic synthesis of prior sections per `references/suggested-actions.md`. Sorted by severity (high → low). Columns: severity, action type, one-line description, source reference. Shows "No suggested actions" when nothing fires. Placed second so stakeholders see the action list immediately after the summary; forward references to later sections are intentional and fine.
- **Coverage analysis** (always): two subsections per `references/gap-analysis.md`. Subsection A — per-journey coverage verdicts (covered/partial/missing) with reasons, how-to counts (strong/weak), other-type counts, and variation status; replaced with "No journeys were supplied for this run." when `journeys = []`. Subsection B — descriptive page counts by Diátaxis type at the builder/maintainer audience tier. No flags; no assertion that any tier must be present.
- **Journey relevance summary** (only if `journeys` was non-empty for the run): Part A — per-journey page counts by Diátaxis type (A1), extended with per-journey weighting comparison (A2) and per-type mix within each journey (A3) when `product-definition/weightings.md` was resolved per `references/weightings.md`, per-journey topic coverage (A4) always when journeys are non-empty per `references/topic-coverage.md`, and per-journey page index (A5) always when journeys are non-empty per `references/journey-matching.md`. Part B — "Pages with no journey match" table. Plus an **"Audience mismatches"** subtable per `references/audience-tagging.md`. If no mismatches were detected, the subtable shows "No audience mismatches detected." See `references/journey-matching.md`, `references/weightings.md`, `references/topic-coverage.md`, and `references/audience-tagging.md` for the column specs.
- **Duplication candidates** (omitted in `coverage-only` mode; otherwise always): clusters of pages sharing the same `(journey, Diátaxis type, variation)` tuple. Includes the explicit "what this does NOT catch" preamble per `references/duplication.md`. Shows "No duplicate candidates detected by the (journey, type, variation) rule." or "Duplication detection requires a journey list; none was supplied." when applicable.
- **Quality flags** (omitted in `coverage-only` mode; otherwise always): pages flagged as `hollow` or carrying `stale-marker` keywords, per `references/quality-flags.md`. Includes the same "what this does NOT catch" preamble. Shows "No hollow pages or stale markers detected." when none found.

### Divider

Insert a markdown horizontal rule (`---`) followed by an H2 heading `## Detail for reviewers` immediately after the Quality flags section. This is the visual boundary at which exec readers may stop. **Omitted in `coverage-only` mode** — the report ends after the Journey relevance summary with no divider.

### Engineer block

**Omitted in `coverage-only` mode** — every table below is skipped, and the placement map that feeds the PERFECT / REWRITTEN / SPLIT / Collisions / Unresolved-links tables is not computed.

- **Coverage by source folder**: table of source dir → counts per category.
- **Copied verbatim (PERFECT)**: table — source path → output path → category → journeys (if any) → audience.
- **Rewritten (single-type)**: table — source path → output path → category → journeys (if any) → audience → one-line reason (which signals or anti-signals fired).
- **Split**: table — source path → list of output paths with categories → journeys (if any) → audience → reason.
- **Outliers (no Diátaxis fit)**: table — source path → why → suggested handling. No journeys or audience columns (outliers do not get journey- or audience-tagged). **Grouping**: outliers with **identical** "why" AND **identical** "suggested handling" SHOULD be collapsed into a single row with the affected paths comma-separated in the source-path cell, and a count prefix (e.g. `12 Nextra nav-landing files: <paths>`). This commonly applies to Nextra `asIndexPage` index pages. Outliers with even slightly different "why" stay individual.
- **Collisions resolved**: table — basename → contributing source paths → final filenames.
- **Unresolved links**: table — file → original link → reason.

### Wrap-up

**Omitted in `coverage-only` mode** — Risk and follow-ups is not produced.

- **Risk and follow-ups**: short prose paragraph for an executive reader covering scope of changes, how to review, and recommended next steps not captured by the fixed action enum in "Suggested actions".

### Path display (report-wide)

If every scanned source file shares a common universal prefix (e.g. all paths begin with `src/content/`), the report MAY strip this prefix from path columns for readability. When prefix stripping is applied:

- State the stripping once in the **Scope** metadata line at the top of the report: `"Scope: 86 .mdx files under src/content/ (paths in tables below are relative to this prefix)."`
- Apply consistently — either all path columns strip the prefix, or none do.

When source paths do not share a common universal prefix, display paths as-is.

## Guardrails
- Never modify, move, rename, or delete files outside the chosen output root (in `full` mode), `<repo_root>/doc-categorise-report.md` (in `report-only` mode), or `<repo_root>/doc-categorise-coverage.md` (in `coverage-only` mode) — that single file per mode is the only thing the skill writes outside `full` mode.
- Never change the meaning, facts, code, or commands of any source document.
- Place low-confidence classifications with the best-guess type and flag them in `REPORT.md` under "Risk and follow-ups" so a human can review post-hoc. Do **not** surface low-confidence cases as in-flight questions to the user — users come to this skill because they do not know Diátaxis, and asking them to make the type call defeats the purpose. Escalate to the user only in extreme cases (foundational page, close call, downstream-impactful) — see "Escalation to the user" in `references/decision-rubric.md` for the criteria and required scoring format. In `coverage-only` mode "Risk and follow-ups" is omitted from the report; low-confidence cases are only surfaced via the classification step's per-page diagnostics.
- If the source list exceeds ~50 files, confirm scope with the user before writing.
- The skill is the only writer of `<output_root>/` (in `full` mode), `<repo_root>/doc-categorise-report.md` (in `report-only` mode), or `<repo_root>/doc-categorise-coverage.md` (in `coverage-only` mode); it must not write anywhere else.

## Invocation examples
```
Use doc-categorise on this repo.
Use doc-categorise on this repo; report only.
Use doc-categorise on this repo; coverage only.
Use doc-categorise on repo/folder/docs; include **/folder/**/*.md; exclude **/file.md.
Use doc-categorise on . ; only the folder-a and folder-b folders.
Use doc-categorise on . ; report-only ; only the folder-a folder.
Use doc-categorise on . ; coverage-only ; only the folder-a folder.
```

## Command templates
```bash
# Enumerate candidate files honouring user globs
find <repo_root> -type f -name '*.md' \
  -not -path '*/node_modules/*' -not -path '*/.git/*' -not -path '*/vendor/*' \
  -not -path '<output_root>/*'

# Quick frontmatter + first-headings sample for classification
awk '/^---$/{c++; next} c==1{print "FM:", $0} c==2 && /^#/{print "H:", $0}' <file>

# Heuristics: imperative-step density (how-to), numbered-step density (tutorial),
# table/field-list density (reference), prose-paragraph density (explanation)
grep -cE '^[0-9]+\.\s' <file>     # numbered steps
grep -cE '^- |^\* '   <file>      # bullets
grep -cE '^\| '        <file>     # tables (reference signal)
grep -cE '^#{1,6} '    <file>     # heading count
```
