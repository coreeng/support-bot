---
name: doc-categorise
description: Use when auditing a repository's markdown documentation for Diátaxis classification, journey-coverage gaps, audience drift, duplication candidates, and quality issues. Produces a stakeholder report identifying coverage gaps and a prioritised list of next actions; optionally also produces a journey-organised copy tree under docs/ (or docs.proposed/) — one folder per user journey, each containing the four Diátaxis subfolders, plus a no-journey/ folder for product-level pages — by rewriting or splitting drift pages. Originals are never modified.
---

# doc-categorise

## When to use
Use this when the user asks to "categorise", "categorize", "audit", "restructure", "rewrite", "Diátaxis-ify", "reorganise into tutorials/how-to/reference/explanation", or "find documentation gaps" in a repository. Outputs are configurable via the execution mode: `full` produces both the rewritten tree and a report; `report-only` produces only the report. In either mode, originals are never edited, moved, renamed, or deleted.

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
- **Quality flags** — a deterministic `hollow` (stub) check and a `stale-marker` check (deterministic `deprecated`/`TODO` keyword prefilter, then a bounded adjudication that discards incidental keyword mentions). See `references/quality-flags.md`.

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
- `references/duplication.md` — load **after gap analysis completes, once per run, always**. Specifies the intentionally minimal duplication-candidate rule: structural grouping by `(journey, Diátaxis type, variation)`, no LLM judgement. Read its "What this does NOT catch" section before interpreting the cluster output.
- `references/quality-flags.md` — load **after duplication detection completes, once per run, always**. Specifies the two quality flags (`hollow`, a pure deterministic rule; and `stale-marker`, a deterministic keyword prefilter followed by a single batched adjudication pass that discards incidental keyword mentions). Read its "What this does NOT catch" section before interpreting the output.
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

until both `product_name` and `journeys` are resolved. `journeys = []` is a valid resolution and the skill MUST proceed in that case. When `journeys` is non-empty it defines the top-level folder structure of the output tree (one folder per journey); when `journeys = []` the tree falls back to a flat Diátaxis layout — see "Output root resolution".

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

The skill has two execution modes:

- **`full` (default)** — produces the categorised tree under the output root (journey-scoped when journeys are supplied, flat Diátaxis layout otherwise — see "Output root resolution") **and** writes REPORT.md inside it. Full behaviour described elsewhere in this file.
- **`report-only`** — produces REPORT.md only. All analysis steps (classification, journey matching, audience tagging, gap analysis, duplication, quality flags, suggested actions) still run, and the placement map is still computed so the report can describe where each page would land. The tree is **not** written: no PERFECT copies, REWRITE outputs, SPLIT outputs, assets, or directories are created.

### Mode resolution (blocking)

The mode is resolved at the start of every run, alongside `product_name` and `journeys`. It is **blocking** — the skill MUST NOT proceed to output root resolution, scanning, classification, or any other step until `mode` is resolved.

1. If the user's invocation contains any case-insensitive match of `report only`, `report-only`, `no tree`, `skip rewrites`, or `analysis only` → set `mode = report-only`. Do not ask.
2. If the invocation contains any case-insensitive match of `full mode`, `with tree`, or `full run` → set `mode = full`. Do not ask.
3. Otherwise, **ASK the user explicitly** with this exact phrasing:

> "Run mode: **full** (writes the rewritten/categorised tree under `docs/` or `docs.proposed/`, AND a `REPORT.md` inside it) or **report-only** (writes only `<repo_root>/doc-categorise-report.md` — no tree, no rewrites)? Reply 'full' or 'report only'."

Accept any answer that matches the trigger phrases above. If the user replies with something ambiguous (e.g. "yes", "idk", "default"), repeat the question — do not pick for them and do not silently default.

The mode is per-run; it is not persisted.

### `report-only` mode specifics

- The single output is `<repo_root>/doc-categorise-report.md`. The output-root resolution rules below do **not** apply.
- The skill does not create any directories.
- Process steps "write generated files" and "copy assets" are skipped.
- The report's structure is identical to a `full`-mode report. "Output path" columns in PERFECT/REWRITE/SPLIT tables describe the path the file *would have been at* in `full` mode (the placement map is computed but not materialised on disk).

This mode is a deliberate temporary affordance for stakeholder demos where the rewritten tree adds noise. It may be removed in a future version of the skill once the default tree-producing behaviour is the more common choice.

## Output root resolution

Applies only in `full` mode. In `report-only` mode, see "Execution mode" above for the single-file output location.

1. Locate the repo's existing docs folder by checking these names in order: `docs/`, `Docs/`, `documentation/`, `Documentation/`. The first match wins.
2. If a match is found → output root is `<repo_root>/<match>.proposed/` (e.g. `docs.proposed/`, `documentation.proposed/`).
3. If no match is found → output root is `<repo_root>/docs/`.
4. The folder layout under the chosen root depends on whether `journeys` was supplied:

   **When `journeys` is non-empty — journey-scoped layout:**
   - One **journey folder** per supplied journey, named with the journey's slug (slug rules below). Create a folder for **every** supplied journey, even one that ends up with no pages — an empty journey folder is a visible coverage gap, not an error.
   - One **`no-journey/`** folder for product-level pages that match no journey. `no-journey` is a reserved folder name.
   - Inside **each** journey folder and inside `no-journey/`, create the four Diátaxis subfolders: `tutorials/`, `how-to/`, `reference/`, `explanation/`.
   - A single shared `assets/` folder directly under the output root. Assets are deduplicated across the whole tree, not per journey.

   **When `journeys = []` — flat layout (fallback):** there are no journeys to scope by, so create the four Diátaxis subfolders directly under the output root (`tutorials/`, `how-to/`, `reference/`, `explanation/`), plus `assets/`. No journey folders and no `no-journey/` folder are created. This is the original layout.
5. Journey-folder slug rules (journey-scoped layout only): lowercase, dash-separated, ASCII only; strip punctuation other than dashes (same rules as the SPLIT slugs in "Placement, naming, and collisions" below). If two journey names slug to the same value, append `-2`, `-3`, … in input order. A journey must never occupy the reserved slug `no-journey`; if one slugs to that value, suffix it (`no-journey-2`).
6. The executive report is written to `<output_root>/REPORT.md`.

Resulting shapes:

```
# journeys non-empty                  # journeys = [] (flat fallback)
<output_root>/                        <output_root>/
  <journey-slug-1>/{4 diátaxis}/        tutorials/
  <journey-slug-2>/{4 diátaxis}/        how-to/
  no-journey/{4 diátaxis}/             reference/
  assets/                             explanation/
  REPORT.md                           assets/
                                      REPORT.md
```

(`{4 diátaxis}` = `tutorials/`, `how-to/`, `reference/`, `explanation/`.)

## Source scope (user-supplied at invocation)
- Required input: repo root path.
- Optional inputs: include globs, exclude globs. Defaults if user gives none: include `**/*.md`; exclude `node_modules/**`, `vendor/**`, `.git/**`, `**/CHANGELOG.md`, `**/LICENSE.md`, the chosen output root itself, and `product-definition/**`.
- The `product-definition/` folder at the repo root is **always** excluded from the documentation scan, regardless of include globs supplied by the user. Journey files are inputs to the skill, not documentation to classify.
- Confirm the resolved file list with the user before writing anything if it exceeds ~50 files.

## Classification

For each source page, assign exactly one of four verdicts:

- **PERFECT-<type>** — the page is unambiguously one Diátaxis type with no drift. It clearly exhibits the type's characteristic signals (per `references/types.md`; not necessarily every signal listed) **and contains zero anti-signals for that type**. A single off-type table, an explanatory digression, or a tutorial flag enumeration disqualifies it — each is named in the type's anti-signal list. Action: copy into the matching subfolder verbatim. "Verbatim" means content-verbatim: the Step 5 frontmatter and the provenance comment are still injected, and any existing frontmatter is preserved-and-merged with the required fields. Mechanical link and asset path rewrites are also allowed.
- **REWRITE-<type>** — the page has a single dominant intent but drifts in tone, structure, or off-type content. Action: rewrite into one file in the dominant category per the rewrite rules below. Preserve every fact, code block, command, and example; reword prose and reorder sections only as needed to fit the type's voice and structure. **A REWRITE MUST change the body.** If, once you attempt the rewrite, the body would come out byte-identical to the source (ignoring injected frontmatter and the provenance comment), then the drift you diagnosed did not actually warrant an edit — the correct verdict is **PERFECT-<type>**, not REWRITE. A verbatim body carrying a REWRITE label is a classification error: by the PERFECT definition above, a content-verbatim output *is* a PERFECT. Never emit a REWRITE whose body equals its source.
- **SPLIT-<types>** — the source page produces multiple output files. Triggered when the page (a) blends two or more Diataxis types (any non-dominant section ≥ ~25% of the page, or any code/step block that belongs to another type), and/or (b) contains multiple independent units — different goals, subjects, or scopes — that each warrant their own page. There is no cap on output count: a README covering ten independent topics may produce ten files. **Each output is strictly single-type.** Action: identify unit boundaries per `references/decision-rubric.md`; output one file per unit; cross-link siblings with a "See also" footer in each file; record the split in the report.
- **OUTLIER** — the page does not fit any Diátaxis type (release notes, meeting minutes, FAQs that are not Q&A reference, scaffolding, etc.). Action: do not place under any category; list in the report under "Outliers" and skip.

Procedure for each page:

1. Apply `references/compass.md`.
2. Verify the candidate type against `references/types.md` signals and anti-signals.
3. If the compass returns one type, no anti-signals fire, and no off-type signals are present → **PERFECT-<type>**.
4. If the compass returns one type but anti-signals or off-type signals are present → **REWRITE-<type>** — but only if fixing that drift will actually change the body. If the page already reads in the dominant type's voice and structure and the "drift" you spotted does not warrant any concrete edit, it is **PERFECT-<type>**, not REWRITE (see the no-op reconciliation in "Rewrite rules").
5. If the compass is ambiguous, content is mixed, or no type fits → load `references/decision-rubric.md` and follow its procedure to reach **SPLIT-<types>** or **OUTLIER**.

## Rewrite rules (no-meaning-change)
- Never invent, drop, or contradict facts, commands, code, parameter names, version numbers, links, or warnings.
- Reword prose for the type's voice (imperative for how-to, lesson voice for tutorial, declarative for reference, discursive for explanation).
- Reshape structure (headings, ordering, lists vs tables) to fit the type.
- Preserve all code fences, command examples, and admonitions verbatim.
- Preserve existing frontmatter; add `product: <product_name>`, `diataxis_type: <category>`, and `source_path: <original repo-relative path>` per Step 5 of the Required input block. Do not change existing `title`/`description` unless empty.
- Prepend a single HTML comment at the top of every generated file (including PERFECT copies): `<!-- Generated by doc-categorise from <source_path>. Do not edit; edit the source. -->`
- **No-op reconciliation (mandatory).** After generating a REWRITE output, compare its body against the source body, **excluding the injected frontmatter block and the provenance comment** (i.e. compare only the content a reader sees). If the two bodies are identical, the REWRITE did no work and MUST be reconciled. **Default to performing the rewrite, not to downgrading** — the downgrade branch is only for pages that never had real drift:
  - **If the page has any anti-signal or off-type content** (the usual reason for a REWRITE — e.g. a reference table or explanatory digression inside a how-to) → you **MUST actually perform the edit** so the body differs: reword to the type's voice, convert or relocate the off-type section, reorder steps. Downgrading to PERFECT is **not permitted** here, because PERFECT is defined as *zero* anti-signals — a page with real drift copied verbatim is neither a valid PERFECT nor an acceptable REWRITE. The reason on the report row must describe a change now visible in the output.
  - **Only if, on reflection, the page had no real drift at all** (the REWRITE was mis-diagnosed and no anti-signal is actually present) → **downgrade the verdict to PERFECT-<type>**. Move the page from the Rewritten table to the Copied-verbatim (PERFECT) table, drop its `review-rewrite` suggested action, and treat it as a verbatim copy for all counts. This branch is the exception, not the escape hatch: if you find yourself downgrading pages that plainly mix types, you are misusing it.

  This check is not optional and applies to every REWRITE output. A REWRITE row whose output equals its source is a defect, not an acceptable outcome. The same rule applies per-output to each single-type file a SPLIT produces: a SPLIT sibling that is byte-identical to the corresponding source span was not actually re-shaped and must be reconciled the same way (fix it, or reconsider whether that boundary warranted a separate file). In `report-only` mode no file is written, so run the reconciliation against the body the rewrite *would* produce: if you cannot articulate a concrete edit that would change the body, the verdict is PERFECT and the row belongs in the Copied-verbatim table.
- **Fact-preservation check (mandatory).** The no-op check guarantees the body *changed*; this check guarantees the change **lost, altered, or invented nothing**. The two run together on every REWRITE output (and every SPLIT output), and both must pass. Procedure:
  1. Before rewriting, extract from the source the set of **load-bearing facts**: every command, code fence, CLI flag, parameter/field/env-var name, version number, numeric value, URL, inter-doc link, file path, admonition/warning, and every value in a data table.
  2. After rewriting, verify each extracted fact still appears in the output with the same meaning. Code fences, command examples, and admonitions must survive **verbatim** (they may move position, but their text may not change). Prose facts (a parameter name, a version, a warning's substance) must be present and unchanged in meaning; wording around them may differ.
  3. Confirm nothing was **invented**: the output introduces no command, flag, value, link, or claim that is not traceable to the source.
  4. If any fact is missing, altered, or added → the rewrite is **not** acceptable. Redo it, keeping the structural/voice changes but restoring exact fidelity. Never ship a REWRITE that trades a fact for better prose.

  This is a bounded check — the fact set is small and extracted once per page, and the source is already in context — so it adds a single verification pass, not a re-read. When a SPLIT distributes facts across siblings, the check is over the **union** of the siblings: every source fact must land in exactly one sibling, and none may be dropped in the seams between them.

## Placement, naming, and collisions

### Placement — which folder(s) a page lands in

Placement is driven by the page's journey-relevance list (from `references/journey-matching.md`) — the same list that sets the audience tier in `references/audience-tagging.md`.

**Journey-scoped layout (`journeys` non-empty):**

- For **every** journey in the page's journey-relevance list — **strong and weak matches alike** — write a copy of the output into `<output_root>/<journey-slug>/<category>/`. A page matching N journeys is therefore copied into N journey folders. The copies are identical except for any inter-doc links that resolve differently per folder. This is intentional duplication so each journey folder is self-contained; the original is never modified.
- If the page's journey-relevance list is **empty** (audience tier `builder/maintainer`), write a single copy into `<output_root>/no-journey/<category>/`.
- For a SPLIT, every split output inherits the source page's journey-relevance list, and each split output is placed (and duplicated across matched journey folders) according to its own single Diátaxis type.

**Flat layout (`journeys = []`):** every output lands directly in `<output_root>/<category>/` — there are no journey folders. Each page produces exactly one copy.

In both layouts, a page is referred to below as living in `<dest>/<category>/`, where `<dest>` is a journey slug, `no-journey`, or (flat layout) absent.

### Filename rules by verdict

- **PERFECT** and **REWRITE** outputs → `<dest>/<category>/<source_basename>.md`. Example: source `notes/foo.md` matching journey "Deploy a workload" and classified how-to → `docs/deploy-a-workload/how-to/foo.md`. In the flat layout the same page would be `docs/how-to/foo.md`.
- **SPLIT** outputs → `<dest>/<category>/<slug>.md`, where `<slug>` is derived from the unit:
  - For a unit that maps to one source heading → slug the heading directly. `## Install on Linux` → `install-on-linux.md`.
  - For a grouped unit (variants of one goal sharing a generated title) → slug the generated title with any leading Diátaxis-type prefix word ("How to", "About", "Why", "Understanding") and any product-name token stripped. `How to install Foglight` → `install.md`. `About Foglight's sampling model` → `sampling-model.md`.
  - If a unit has no natural heading (rare; e.g. the only how-to portion of an otherwise single-type source), fall back to the source basename.

Slug rules: lowercase, dash-separated, ASCII only. Strip punctuation other than dashes.

### Collision resolution

Collisions are evaluated **per destination folder** (`<dest>/<category>/`). If two outputs would share the same filename within the same destination folder, append a path-derived slug suffix from the source path. `foo.md` from `folder-a/onboarding/foo.md` becomes `foo--folder-a-onboarding.md`.

Two cases that are **not** collisions: outputs in different category folders, and outputs in different journey folders (including the same source page deliberately duplicated across journey folders — those copies are expected, not a clash).

## Links and assets
- Inter-doc markdown links (`[text](path.md)` or `[text](path.md#anchor)`):
    - If the target page is also being placed under the output tree → rewrite to a copy of the target, computed **relative to the current file**. In the journey-scoped layout the target may have several copies (one per matched journey, or a single `no-journey/` copy); prefer the target copy that lives in the **same journey folder** as the current file. If the target was not placed in the current file's journey folder, link to the target's copy in its first-listed matched journey folder (journey input order), or its `no-journey/` copy if the target matched no journey. In the flat layout there is exactly one target copy.
    - If the target is outside the categorized set → leave as-is and flag in the report under "Unresolved links".
- Image and asset references (`![alt](path)` or HTML `<img src=...>`): copy the asset into the shared `<output_root>/assets/`, dedup by content hash, rename on collision with a path-derived slug. Update the reference in the generated file to point at the copied asset, computed **relative to the generated file's location**. Note the depth differs by layout: in the journey-scoped layout a generated file sits at `<output_root>/<dest>/<category>/`, two levels below the root (typically `../../assets/<file>`); in the flat layout it sits one level below (`../assets/<file>`).
- External `http(s)://` links are left untouched.

## Process
1. Resolve `product_name`, `journeys`, and execution `mode` per the Required input block and the Execution mode section. All three are blocking. Confirm the resolved values with the user before continuing.
2. Resolve output root and the folder layout per "Output root resolution" (journey-scoped — one folder per journey plus `no-journey/`, each with the four Diátaxis subfolders — when `journeys` is non-empty; flat four-Diátaxis layout when `journeys = []`), and the source file list. Print the output root, the resolved folder structure (the journey folders, if any), and the file list back to the user before writing.
3. For each source file: extract frontmatter, headings, and a content sample; classify per the Classification section (applying `references/compass.md` and `references/types.md`, escalating to `references/decision-rubric.md` for ambiguous cases) as PERFECT-<type>, REWRITE-<type>, SPLIT-<types>, or OUTLIER.
4. If `journeys` is non-empty, tag each source file with journey relevance per `references/journey-matching.md`. Skipped entirely when `journeys = []`.
5. Tag each source file with audience per `references/audience-tagging.md`. Always runs. Uses journey-relevance results from step 4 when available; falls back to content inference when no journey match exists or the matched journey has no `users:` field.
6. Compute coverage gaps per `references/gap-analysis.md`. Always runs. Produces a per-journey coverage verdict (covered/partial/missing) — Part A skipped when `journeys = []` — and descriptive product-level page counts at the builder/maintainer audience tier (Part B always).
7. Identify duplication candidates per `references/duplication.md`. Always runs (section appears in REPORT.md either way); pages without strong journey matches are not analysed.
8. Apply quality flags per `references/quality-flags.md`. Always runs. `hollow` (mostly empty page) is a deterministic check; `stale-marker` is a deterministic deprecation/TODO keyword prefilter followed by a single batched adjudication pass that keeps only genuine staleness markers and discards incidental keyword mentions.
9. Synthesise suggested actions per `references/suggested-actions.md`. Always runs. Walks the outputs of steps 3–8 and emits one action per matching signal from a fixed enum of nine action types and three severity tiers.
10. Build the global placement map (destination folder(s) per page + paths + collision resolution) per "Placement, naming, and collisions" — including the per-journey duplication of pages that match multiple journeys. Always runs — the placement map is needed for the REPORT.md tables even in `report-only` mode.
11. In `full` mode: write generated files (duplicating each page into every matched journey folder, or into `no-journey/`, per the placement map), copy assets, rewrite links. **Skipped in `report-only` mode.**
12. Write the report. In `full` mode: `<output_root>/REPORT.md`. In `report-only` mode: `<repo_root>/doc-categorise-report.md`.
13. Print a one-screen summary in chat with counts and a pointer to the report.

## Executive report format (`REPORT.md`)

REPORT.md is organised in two reader-priority blocks separated by a divider. The **exec block** carries actions, gaps, and signal summaries — what an executive reader needs. The **engineer block** carries per-page classification detail behind a `## Detail for reviewers` H2 divider that lets exec readers stop at a clear boundary.

### Cross-section consistency invariants (read before generating any section)

The per-page journey-relevance and audience tags emitted in REPORT.md's Sections 8 (Copied verbatim) and 9 (Rewritten) are the **ground truth** for every aggregated count elsewhere in the report. The following invariants MUST hold across the whole report. They are not aspirational — they are required, and the agent MUST self-check every aggregated count against the per-page tags before emitting the report.

1. **Journey coverage table (Section 3 Subsection A)** — for every journey row, `strong_how_to_count` and `weak_how_to_count` MUST equal the count of pages in Sections 8/9 tagged with that journey at the corresponding confidence, restricted to `how-to` Diátaxis verdicts. See `references/gap-analysis.md` Step 4b for the explicit self-check.
2. **Per-journey page count table (Section 4 Part A)** — for every journey row, the per-Diátaxis-type cell counts MUST equal the count of pages in Sections 8/9 tagged with that journey at any confidence, broken down by Diátaxis type. The parenthetical `(N weak)` annotation MUST equal the count restricted to weak matches.
3. **Duplication clusters (Section 5)** — every cluster MUST consist of pages tagged with the cluster's journey at confidence `strong` in Sections 8/9, with matching Diátaxis type and variation.
4. **Suggested actions (Section 2)** — every action MUST correspond 1:1 with a triggering signal as defined in `references/suggested-actions.md`. The "Top 3 risks" list MUST be the first three rows of the same action table.
5. **Rewritten table (Section 9) reflects real edits** — every row in the Rewritten (single-type) table MUST correspond to an output whose body differs from its source (ignoring injected frontmatter and the provenance comment). A page whose generated body is byte-identical to its source is a PERFECT, not a REWRITE: it MUST appear in the Copied-verbatim (PERFECT) table instead, and it MUST NOT emit a `review-rewrite` action. Run the no-op reconciliation in "Rewrite rules" before emitting the report; do not list a verbatim output as rewritten. The same applies per-output to SPLIT siblings — a split output identical to its source span is a defect to reconcile, not a row to emit.

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

- **Summary**: a single concise metric table. The metrics it MUST contain — and the only metrics it may contain — are:
  - Total source files scanned.
  - Counts per Diátaxis category (tutorial / how-to / reference / explanation).
  - Counts of PERFECT / REWRITTEN / SPLIT / OUTLIER.
  - Asset count.
  - Unresolved-link count.
  - Audience tier counts, exact format: `end-user X, builder/maintainer Y`.
  - Suggested actions count, exact format: `Suggested actions: N (high X · medium Y · low Z)`.
  - **Only when `mode = report-only`**: a projected-output line with exact wording: `Output files that would be written in 'full' mode (excludes outliers): ~N. This run is 'report-only'; no files were actually written.` The wording is **mandatory** — do not abbreviate to "Output files in full mode" or similar, because that phrase is opaque to readers who haven't read the header metadata.

  Do **not** repeat the output root or the run mode in the table — both are already in the header metadata above. The skill MUST NOT add metrics beyond those listed above. If a new metric becomes useful, it belongs in a new section, not appended to the Summary.
- **Suggested actions** (always): deterministic synthesis of prior sections per `references/suggested-actions.md`. Sorted by severity (high → low). Columns: severity, action type, one-line description, source reference. Shows "No suggested actions" when nothing fires. Placed second so stakeholders see the action list immediately after the summary; forward references to later sections are intentional and fine.
- **Coverage analysis** (always): two subsections per `references/gap-analysis.md`. Subsection A — per-journey coverage verdicts (covered/partial/missing) with reasons, how-to counts (strong/weak), other-type counts, and variation status; replaced with "No journeys were supplied for this run." when `journeys = []`. Subsection B — descriptive page counts by Diátaxis type at the builder/maintainer audience tier. No flags; no assertion that any tier must be present.
- **Journey relevance summary** (only if `journeys` was non-empty for the run): per-journey page-count table by Diátaxis type, plus a "Pages with no journey match" table, plus an **"Audience mismatches"** subtable per `references/audience-tagging.md`. If no mismatches were detected, the subtable shows "No audience mismatches detected." See `references/journey-matching.md` and `references/audience-tagging.md` for the column specs.
- **Duplication candidates** (always): clusters of pages sharing the same `(journey, Diátaxis type, variation)` tuple. Includes the explicit "what this does NOT catch" preamble per `references/duplication.md`. Shows "No duplicate candidates detected by the (journey, type, variation) rule." or "Duplication detection requires a journey list; none was supplied." when applicable.
- **Quality flags** (always): pages flagged as `hollow` or carrying genuine `stale-marker` keywords (incidental keyword mentions are adjudicated out), per `references/quality-flags.md`. Includes the same "what this does NOT catch" preamble, and — when any keyword candidates were dismissed as incidental — the auditable dismissed-candidates tally line. Shows "No hollow pages or stale markers detected." when none found.

### Divider

Insert a markdown horizontal rule (`---`) followed by an H2 heading `## Detail for reviewers` immediately after the Quality flags section. This is the visual boundary at which exec readers may stop.

### Engineer block

In the journey-scoped layout a page that matches multiple journeys is copied into one folder per matched journey (see "Placement, naming, and collisions"). Wherever a table below has an "output path" column, list **every** destination copy for that page (comma-separated or one per line in the cell), not just the first.

- **Coverage by source folder**: table of source dir → counts per category.
- **Copied verbatim (PERFECT)**: table — source path → output path(s) → category → journeys (if any) → audience.
- **Rewritten (single-type)**: table — source path → output path(s) → category → journeys (if any) → audience → one-line reason (which signals or anti-signals fired).
- **Split**: table — source path → list of output paths with categories → journeys (if any) → audience → reason.
- **Outliers (no Diátaxis fit)**: table — source path → why → suggested handling. No journeys or audience columns (outliers do not get journey- or audience-tagged). **Grouping**: outliers with **identical** "why" AND **identical** "suggested handling" SHOULD be collapsed into a single row with the affected paths comma-separated in the source-path cell, and a count prefix (e.g. `12 Nextra nav-landing files: <paths>`). This commonly applies to Nextra `asIndexPage` index pages. Outliers with even slightly different "why" stay individual.
- **Collisions resolved**: table — basename → contributing source paths → final filenames.
- **Unresolved links**: table — file → original link → reason.

### Wrap-up

- **Risk and follow-ups**: short prose paragraph for an executive reader covering scope of changes, how to review, and recommended next steps not captured by the fixed action enum in "Suggested actions".

### Path display (report-wide)

If every scanned source file shares a common universal prefix (e.g. all paths begin with `src/content/`), the report MAY strip this prefix from path columns for readability. When prefix stripping is applied:

- State the stripping once in the **Scope** metadata line at the top of the report: `"Scope: 86 .mdx files under src/content/ (paths in tables below are relative to this prefix)."`
- Apply consistently — either all path columns strip the prefix, or none do.

When source paths do not share a common universal prefix, display paths as-is.

## Guardrails
- Never modify, move, rename, or delete files outside the chosen output root (or `<repo_root>/doc-categorise-report.md` in `report-only` mode — that single file is the only thing the skill writes in that mode).
- Never change the meaning, facts, code, or commands of any source document.
- Place low-confidence classifications with the best-guess type and flag them in `REPORT.md` under "Risk and follow-ups" so a human can review post-hoc. Do **not** surface low-confidence cases as in-flight questions to the user — users come to this skill because they do not know Diátaxis, and asking them to make the type call defeats the purpose. Escalate to the user only in extreme cases (foundational page, close call, downstream-impactful) — see "Escalation to the user" in `references/decision-rubric.md` for the criteria and required scoring format.
- If the source list exceeds ~50 files, confirm scope with the user before writing.
- The skill is the only writer of `<output_root>/` (in `full` mode) or `<repo_root>/doc-categorise-report.md` (in `report-only` mode); it must not write anywhere else.

## Invocation examples
```
Use doc-categorise on this repo.
Use doc-categorise on this repo; report only.
Use doc-categorise on repo/folder/docs; include **/folder/**/*.md; exclude **/file.md.
Use doc-categorise on . ; only the folder-a and folder-b folders.
Use doc-categorise on . ; report-only ; only the folder-a folder.
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
