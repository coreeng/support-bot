# doc-categorise

A standalone agentic skill that reads a repository's markdown documentation, classifies every page against the four [Diátaxis](https://diataxis.fr/) types, and produces:

1. A fresh, Diátaxis-organised copy of the docs alongside the originals.
2. An executive report (`REPORT.md`) for stakeholder review.

Originals are never modified, moved, renamed, or deleted.

This README documents how to run the skill and how to interpret its output. The skill's logic lives in `SKILL.md` and the supporting reference files under `references/`; this README is for humans, those files are for the agent.

> **Note on examples:** the reference file `references/examples.md` documents a fictional product called *Foglight*. Foglight is invented for these examples only — it is not a real product and any resemblance to existing tools is coincidence.

---

## What the skill does, in one paragraph

When pointed at a repo, the skill walks every markdown file it finds, applies a Diátaxis decision procedure to classify each page (tutorial / how-to / reference / explanation), and writes a new categorised tree under `docs/` (or `docs.proposed/` if `docs/` already exists). Pages that already match one type cleanly are copied verbatim. Pages that drift in tone or structure are rewritten into single-type files preserving every fact. Pages that blend multiple intents or contain multiple independent topics are split into multiple files. Pages that do not fit any Diátaxis type (changelogs, meeting minutes, etc.) are listed as outliers. The whole run is summarised in `REPORT.md`.

---

## Quick start

1. Make the skill available to your agent (see "Installation" below).
2. Invoke the skill by name from your agent. For example:
   ```
   Use doc-categorise on this repo.
   ```
3. The skill will pause and ask:
   > What product is this documentation for?

   Type the product name (e.g. `Acme Widget`). If the docs are not tied to a specific product, type `not applicable`.
4. The skill confirms the file list before writing.
5. When the run completes, open `<repo>/docs/REPORT.md` (or `docs.proposed/REPORT.md`) for the summary.

That is the happy path. No flags, no config files.

---

## Installation

**Prerequisite:** GitHub CLI **2.90.0 or later** — the `gh skill install` command was introduced as part of the `gh skill` feature set in that release. Check your version with `gh --version`; upgrade via your package manager if you're on an older one (e.g. `brew upgrade gh` on macOS).

Install via the `gh skill` extension. The general shape:

```bash
gh skill install coreeng/support-bot doc-categorise \
  --agent <agent> \
  --pin <branch-or-ref>
```

**Agent options**:

| Agent | `--agent` value |
| ------ | --------------- |
| Claude Code | `claude-code` |
| Augment | `augment` |

**Pre-merge install** (the skill currently lives on the `doc_categorise_skill` branch — use `--pin` to fetch it before it lands on `main`):

```bash
# Claude Code
gh skill install coreeng/support-bot doc-categorise \
  --agent claude-code \
  --pin doc_categorise_skill

# Augment
gh skill install coreeng/support-bot doc-categorise \
  --agent augment \
  --pin doc_categorise_skill
```

**Post-merge install** (once `doc_categorise_skill` is merged to `main`, drop `--pin`):

```bash
gh skill install coreeng/support-bot doc-categorise --agent claude-code
gh skill install coreeng/support-bot doc-categorise --agent augment
```

The entry file is `SKILL.md`. The eight files under `references/` are loaded on demand by the agent during a run.

---

## What the skill needs from you

| Input                              | When asked                       | What it is                                                                                                                                                  |
| ---------------------------------- | -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Repo root path                     | At invocation                    | The directory the skill scans. Defaults to the agent's current working directory.                                                                           |
| `product-definition/` folder       | Optional, at the repo root       | Pre-supplies the product name and journey list so the skill does not need to prompt. See "Pre-supplying the product and journeys" below for the schema.     |
| Product name                       | Blocking, asked first (fallback) | Asked only if `product-definition/product.md` is missing or invalid. Free-text. Type `not applicable` if not relevant.                                      |
| Journey list                       | Blocking (fallback)              | Asked only if no `product-definition/journeys/` was found. Paste one journey per line or as markdown bullets. Type `not applicable` if there are none.      |
| Include globs                      | Optional, at invocation          | Narrow the source set, e.g. `docs/**/*.md`. Default: `**/*.md`.                                                                                              |
| Exclude globs                      | Optional, at invocation          | Skip files. Default excludes: `node_modules/**`, `vendor/**`, `.git/**`, `**/CHANGELOG.md`, `**/LICENSE.md`, and `product-definition/**`.                    |

The skill will not start scanning, classifying, or writing until it has both `product_name` and a `journeys` list (which may be empty). This is a hard gate — if your agent appears to skip the questions, the agent did not load the skill.

### Pre-supplying the product and journeys

You can put the product and its journeys into a `product-definition/` folder at the repo root. If present, the skill reads it on startup instead of prompting. Schema (see `references/product-definition.md` in the skill for the authoritative spec):

```
<repo>/
  product-definition/
    product.md            # frontmatter: name (required), owners (required), features (optional)
    journeys/
      <slug>.md           # frontmatter: name (required); description, users, feature, variations (all optional)
      <slug>.md
      ...
```

The skill is **read-only** with respect to this folder — it never creates, edits, or scaffolds it. If `product-definition/product.md` is missing or invalid, the skill falls back to asking you interactively for both the product name and the journey list. Pasted input lives in memory for the run only; it is not written back to disk.

The folder is always excluded from the documentation scan.

---

## How to invoke

| To do this                                          | Say this                                                                                                  |
| --------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Categorise everything in the current repo           | `Use doc-categorise on this repo.`                                                                         |
| Produce only the report (no rewritten tree)         | `Use doc-categorise on this repo; report only.`                                                            |
| Categorise only a subfolder                         | `Use doc-categorise on repo/docs.`                                                                         |
| Categorise with custom include/exclude globs        | `Use doc-categorise on . ; include docs/**/*.md ; exclude **/draft-*.md`                                  |
| Re-run after fixing inputs                          | Same invocation. Re-runs are safe: outputs always go to `docs/` or `docs.proposed/`, never on top of originals. |

If the repo has more than ~50 markdown files, the skill confirms the source list with you before writing anything.

---

## What you get out

### Execution modes

Two modes:

- **Full** — produces the rewritten/categorised tree under `docs/` or `docs.proposed/`, **plus** `REPORT.md` inside that tree. Use this when you want both the structural rewrite output and the analysis.
- **Report-only** — produces only `<repo>/doc-categorise-report.md` at the repo root. No tree, no rewrites, no asset copying. All analysis (classification, journey matching, audience tagging, gap analysis, duplication, quality flags, suggested actions) still runs; the report describes where each page *would* have landed in full mode.

**The skill asks which mode to use at the start of every run** unless your invocation explicitly opts in. There is no silent default — the question is blocking.

Trigger phrases (case-insensitive, anywhere in your invocation) skip the question:

| Phrase | Mode |
| --- | --- |
| `report only`, `report-only`, `no tree`, `skip rewrites`, `analysis only` | report-only |
| `full mode`, `with tree`, `full run` | full |

Examples:
- `Use doc-categorise on this repo.` → skill asks you to pick at the start.
- `Use doc-categorise on this repo; report only.` → no prompt, goes straight to report-only.
- `Use doc-categorise on this repo; full mode.` → no prompt, goes straight to full.

Report-only is a deliberate temporary affordance for stakeholder demos where the rewritten tree adds noise. It may be removed once the team is comfortable with the full-mode behaviour.

### Directory layout

After a successful run **in full mode** on a repo that did not already have a `docs/` folder:

```
<repo>/
├── README.md                            (untouched)
├── src/...                              (untouched)
├── notes/                               (untouched, originals)
├── product-definition/                  (optional input you create — never written by the skill)
│   ├── product.md
│   └── journeys/
│       └── <slug>.md
└── docs/                                ← new — Diátaxis-organised tree
    ├── tutorials/
    ├── how-to/
    ├── reference/
    ├── explanation/
    ├── assets/                          (images and other assets, deduplicated)
    └── REPORT.md
```

If `docs/` already existed, the output tree is at `docs.proposed/` instead and the original `docs/` is left untouched. The skill also recognises `Docs/`, `documentation/`, and `Documentation/` as existing docs folders.

### Frontmatter on every output file

Every file the skill writes carries this frontmatter block (in addition to any existing frontmatter from the source):

```yaml
---
product: "<product name>"
diataxis_type: "<tutorial|how-to|reference|explanation>"
source_path: "<path to original file>"
---
```

When a `product-definition/` folder is in use and the source page matched at least one journey, the frontmatter also carries a `journeys:` field listing each match with its variation (if any) and confidence tier. Every output file also carries an `audience:` block giving the audience tier (`builder/maintainer` vs `end-user` per the team's "good docs" definition) and detailed labels:

```yaml
---
product: "Core Platform"
diataxis_type: "how-to"
source_path: "docs/deploy-staging.md"
journeys:
  - name: Deploy a workload
    variation: staging
    confidence: strong
audience:
  tier: end-user
  labels: [end-user, application-developer]
  confidence: strong
  source: journey
---
```

If the page matched no journeys, the `journeys:` field is omitted entirely and the audience tier is `builder/maintainer` with labels inferred from page content. When journey-supplied audience disagrees with content-inferred audience, the `audience:` block carries an additional `mismatch:` field listing both — a high-value signal for stakeholder review.

Plus a provenance comment immediately below the frontmatter:

```html
<!-- Generated by doc-categorise from <source_path>. Do not edit; edit the source. -->
```

### The four Diátaxis types

| Type        | What it is                                                                  | Reader is asking…       |
| ----------- | --------------------------------------------------------------------------- | ----------------------- |
| Tutorial    | A learning lesson — beginner walks through to a guaranteed outcome.         | "Can you teach me to…?" |
| How-to      | A recipe — competent reader has a specific goal and follows steps.          | "How do I…?"            |
| Reference   | Factual lookup — APIs, flags, schemas, exit codes; read one entry, leave.    | "What is…?"             |
| Explanation | Discursive essay — the *why*, trade-offs, design rationale.                 | "Why…?"                 |

See `references/types.md` for full signals, anti-signals, and disambiguation rules.

### Verdict labels and how they map to common Diátaxis vocabulary

The skill assigns each source page one of four verdicts. These map to common Diátaxis-style labels as follows:

| Common label  | This skill's verdict                          | Where it lands                                                  |
| ------------- | ---------------------------------------------- | --------------------------------------------------------------- |
| tutorial      | PERFECT-tutorial or REWRITE-tutorial           | `tutorials/`                                                    |
| how-to        | PERFECT-how-to or REWRITE-how-to               | `how-to/`                                                       |
| reference     | PERFECT-reference or REWRITE-reference         | `reference/`                                                    |
| explanation   | PERFECT-explanation or REWRITE-explanation     | `explanation/`                                                  |
| mixture       | SPLIT-\<types>                                  | Multiple output files (one per single-type slice), cross-linked |
| unknown       | OUTLIER                                        | Not placed; recorded in `REPORT.md` under "Outliers"            |

PERFECT means the page was clean enough to copy without rewriting. REWRITE means the type was clear but the page drifted and was rewritten into the dominant type (preserving every fact). SPLIT means the page produced multiple output files. OUTLIER means the page does not fit any of the four types.

---

## How to read REPORT.md

`REPORT.md` is organised in two reader-priority blocks separated by a `## Detail for reviewers` horizontal-rule divider. The **exec block** (sections 1–6) carries actions, gaps, and signal summaries — what an executive reader needs. The **engineer block** (sections 7–13) carries per-page classification detail behind the divider, so executive readers can stop at the boundary. Section 14 is a prose wrap-up.

Within the exec block, section 4 (Journey relevance summary) appears only when a journey list was supplied; sections 2 (Suggested actions), 3 (Coverage analysis), 5 (Duplication candidates), and 6 (Quality flags) always appear though some content is journey-dependent. Here is what each section tells you and what to do about it.

### Exec block

### 1. Summary

Top-level counts: total source files scanned, output root used, counts per category, counts per verdict, asset count, unresolved-link count, audience tier counts (`end-user X, builder/maintainer Y`), and suggested-actions count (`Suggested actions: N (high X · medium Y · low Z)`). **Sanity-check first.** If the totals look obviously off (e.g. zero references in a CLI tool repo, zero end-user pages despite a non-empty journey list, or zero high-severity actions on a docs set you know has gaps), the source scope is probably wrong or the journey list is mis-supplied.

### 2. Suggested actions

A single prioritised list of recommended next actions, synthesised deterministically from the analysis in sections 3–6 plus the REWRITE/SPLIT verdicts in the engineer block. Each action is one of ten fixed types (`write-how-to`, `write-builder-doc`, `complete-how-to`, `strengthen-how-to`, `realign-audience`, `consolidate-cluster`, `expand-stub`, `clean-stale-markers`, `review-rewrite`, `review-split`) with one of three severities (`high` / `medium` / `low`). Sorted high → low. Columns: severity, type, one-line description, source reference (the page, journey, or cluster the action derives from).

**The skill does NOT invent actions outside the enum.** If a signal needs an unusual response, it lives in section 14 (Risk and follow-ups), not here. The skill does NOT LLM-judge severity — the mapping from signal type to severity is fixed.

**Severity calibration**:
- `high` — gaps in journey-level how-to coverage (missing how-tos for supplied journeys) and product-level R/E/H presence (zero pages of R, E, or H for builders/maintainers). These block the team's "good docs" expectations.
- `medium` — drift signals: incomplete coverage (missing variations or not end-to-end), audience mismatch, duplication clusters.
- `low` — review prompts (REWRITE/SPLIT verdicts) and cleanup tasks (hollow pages, stale markers).

If no actions were emitted, the section shows "No suggested actions — no signals from prior sections produced an action."

**Action:** work the list high-severity first. Spot-check one or two actions per type against the section they derive from before doing the work — the synthesis is deterministic, but the underlying classifications may have edge cases worth a human eye. Forward references to sections 3–6 below the action list are intentional: exec readers either trust the list and stop, or read on.

### 3. Coverage analysis

The headline gap section for stakeholder discussion. Two subsections.

**Subsection A — Journey coverage** (empty when no journeys were supplied). A table with one row per journey, sorted `missing` first, then `partial`, then `covered`. Columns: journey name, verdict, how-to coverage (`X strong, Y weak`), other types matched, variation status (`linux ✓ · macos ✗ · windows ✓` if the journey has variations, otherwise `—`), and reasons (empty for `covered`; otherwise specific gaps like `missing variations: macos, windows` or `not end-to-end: rollback step is not documented`).

**Subsection B — Product-level coverage** (always). Page counts by Diátaxis type for builder/maintainer audience pages (reference, explanation, how-to, tutorial). A flag fires when any of R/E/H is zero — the team's "good docs" definition expects all three for product-level documentation.

**Use Subsection A** to identify the journeys that need work and what specifically is missing. **Use Subsection B** to see whether the product itself has the R/E/H foundation aimed at builders/maintainers. Each row of Subsection A drives one or more entries in section 2 (Suggested actions).

### 4. Journey relevance summary (only if journeys were supplied)

Three parts. **Part A** is a table of every supplied journey → page count by Diátaxis type (tutorial / how-to / reference / explanation / total). Weak matches may appear in parentheses, e.g. `5 (2 weak)`. **Part B** is a "Pages with no journey match" table listing every scanned page that did not match any journey, with the page's source path, assigned Diátaxis type, and a short content hint. **Part C** is an "Audience mismatches" table listing every page where the journey-supplied audience disagreed with the content-inferred audience — columns: source path, matched journey(s), authored audience, inferred audience, inferred confidence. If no mismatches were detected, Part C shows "No audience mismatches detected."

**Use Part A** to spot under-covered journeys (a row of mostly zeros means the journey lacks docs). **Use Part B** to spot off-strategy content (a page that doesn't fit any of your journeys might be scaffolding, product-level reference for builders, or genuinely outside the documentation strategy). **Use Part C** to spot drift — pages tagged to an end-user journey but written in builder language are a leading indicator that the doc has been overtaken by implementation details. None of these tables makes a decision for you; they make the gaps and drifts visible so you can decide per row.

### 5. Duplication candidates

Clusters of pages that share the same `(journey, Diátaxis type, variation)` tuple — i.e. two or more pages tagged to the same journey, classified as the same Diátaxis type, and matched to the same variation (or all with no variation). Columns: journey, type, variation, size, page list.

**The skill does NOT confirm these are actually duplicates.** There is no semantic check, no LLM judgement, and no similarity threshold. The rule is purely structural. Pages sharing the same tuple are flagged as **candidates for consolidation**; the stakeholder decides per cluster whether to merge, keep both, or take some other action.

**What this catches**: the common rot pattern — two how-tos for the same journey + variation, both still in the docs tree, neither author having found the other.

**What this does NOT catch** (deliberately out of scope for this version):
- Pages with no journey tag (product-level documentation duplication).
- Pages tagged to different journeys but saying roughly the same thing.
- Pages with the same content but different Diátaxis types (e.g. a reference and an explanation that drift into the same material).
- Semantic similarity across variations of the same journey (variants are legitimately separate).
- Pages with only weak journey matches.

If no clusters are found, the section shows "No duplicate candidates detected by the (journey, type, variation) rule." If `journeys` was empty, the section shows "Duplication detection requires a journey list; none was supplied."

**Action:** spot-check each cluster. False positives are cheap to dismiss; the cost of this step lies in not flagging cases the rule doesn't cover (see the "does NOT catch" list above) — those need a different tool.

### 6. Quality flags

Pages flagged by two deterministic checks: `hollow` (the page has fewer than ~10 non-blank content lines and no code blocks or tables — likely a stub) and `stale-marker` (the page contains explicit keywords like `deprecated`, `TODO:`, `FIXME:`, `obsolete`, `legacy`, `do not use`). Each flagged page shows the flag(s) it triggered and the specific reasons (line numbers and matched keywords for stale markers; one-line summary for hollow).

**The skill does NOT confirm these are actually problems.** A `stale-marker` may be a quoted example or a code identifier — false positives are visible (the matching line is shown) and the stakeholder dismisses them per row.

**What this catches**: unfinished stubs and explicit deprecation/TODO content the author left in the file.

**What this does NOT catch** (deliberately out of scope for this version):
- Vague prose that is well-formed but says nothing useful.
- Factual contradictions between pages.
- Stale-by-context (a page that's implicitly outdated without saying `deprecated`).
- Style or tone inconsistency.

Low-confidence Diátaxis classifications and drift between types are already surfaced elsewhere (section 14 Risk and follow-ups, and the REWRITE/SPLIT verdicts in the engineer block), so they are not duplicated here.

If no pages were flagged, the section shows "No hollow pages or stale markers detected."

**Action:** spot-check each flagged page. A small number of false positives is expected; the cost of this step is in what it doesn't cover (see the "does NOT catch" list above), not in what it overreports.

---

### `## Detail for reviewers` (divider in REPORT.md)

A horizontal rule + H2 in REPORT.md that marks the boundary at which executive readers may stop. The sections below provide the per-page classification detail used to derive the exec-block sections above.

### Engineer block

### 7. Coverage by source folder

A table of source directory → counts per Diátaxis type. **Use this to spot lopsided coverage by where docs live.** A folder that is 100% reference and 0% how-to may be missing user-facing recipes; a folder that is 100% tutorial may be missing the day-to-day operations docs.

### 8. Copied verbatim (PERFECT)

Pages clean enough to copy without rewriting. Source path → output path → category → journeys (if any) → audience. **Action:** spot-check a couple to confirm the agent agreed with you. PERFECT calls are conservative; if you see suspiciously many, the agent may be under-counting drift.

### 9. Rewritten (single-type)

Pages with a clear dominant intent that drifted in tone or structure, rewritten into the dominant type. Includes a one-line reason (which signals or anti-signals fired), the journeys the page covers (if any), and the audience. **Action:** review the rewritten file against its source. The agent preserves facts but rewords prose; check that the meaning is unchanged.

### 10. Split

Pages that became multiple output files — either a mixture of types, or multiple independent topics, or both. Lists every output, the journeys it covers (if any), its audience, and a one-line reason. **The highest-value section to review with stakeholders.** Splits are visible structural changes; the unit breakdown should match what stakeholders expect.

### 11. Outliers (no Diátaxis fit)

Pages that did not fit any of the four types — release notes, meeting minutes, marketing material, scaffolding, etc. Lists why and suggests handling. **Action:** decide per row whether each outlier should be kept in place, moved to a separate folder (e.g. `changelogs/`), or removed. Outliers are not journey-tagged.

### 12. Collisions resolved

When two pages would have produced the same output filename, the skill appends a path-derived slug to disambiguate. Lists basename → contributing sources → final filenames. **Action:** usually fine. Investigate only if the suffixes are noisy.

### 13. Unresolved links

Inter-doc links the skill could not rewrite — typically links from inside the categorised set to files outside it. **Action:** check whether the link targets should also be in the doc set; if so, expand the source scope and re-run.

### Wrap-up

### 14. Risk and follow-ups

A short prose paragraph for stakeholder reading. Covers the scope of the run, low-confidence classifications that need a human eye, and recommended next steps not captured by the fixed action enum in section 2.

---

## Common scenarios

### A well-maintained repo
Most pages classify as PERFECT or REWRITE; few splits; few outliers. The run is fast and the report is short. Use the categorised tree directly.

### A sprawling legacy README
A single mega-README is the most common pattern at the start of a docs cleanup. Expect:
- The README itself classified as SPLIT, producing multiple output files (one per topic).
- A coverage skew: lots of how-to, less of everything else.
- A handful of outliers (the "About this project" section, the changelog block, etc.).

Section 10 (Split) of REPORT.md is the focus. Review the unit breakdown with stakeholders.

### A multi-product monorepo
The skill currently uses one product name per run. For a monorepo, either run the skill per product (specifying include globs) or use `not applicable` and add product context manually post-run.

### A repo with no clear docs structure
If every file lands as OUTLIER or REWRITE, the source repo probably needs editorial work before classification adds value. The report is still useful — it makes the gaps explicit.

---

## What this skill does not do (yet)

These are known gaps relative to the broader documentation strategy:

- **Journey context.** The skill captures product, but not user journey (onboarding, day-2 operations, migration, etc.). Journey-tagging is on the roadmap.
- **Audience inference.** The Diátaxis type implies audience (tutorial → beginner, how-to → competent practitioner), but the report does not yet surface finer audience tells (e.g. "assumes Kubernetes experience"). Free-text audience extraction is planned.
- **Content-similarity duplication detection.** The skill detects filename collisions, but not two pages that say roughly the same thing under different names.
- **Per-page suggested actions.** The current report has high-level recommendations in section 9, not per-row action suggestions.

These will be added incrementally. The skill is useful today without them.

---

## Troubleshooting

**The agent does not ask for a product name.**
Either (a) the skill found a valid `product-definition/product.md` at the repo root and used it — check that the resolved product name was printed back to you; or (b) the skill did not load. The blocking input gate is the first thing the skill should do; if neither the gate nor a folder-resolution confirmation appeared, check that `SKILL.md` is in the skill path and try invoking by name explicitly.

**The agent reports one or more journey files were skipped.**
The most common cause is missing required fields. `product-definition/product.md` needs a non-empty `name` and `owners`; each `journeys/*.md` needs a non-empty `name`. Files with missing required fields are skipped and listed in the run summary. Fix the offending frontmatter and re-run, or accept the skip and paste the missing journeys at the prompt.

**The agent classifies too many pages as OUTLIER.**
Either the source scope is wrong (it picked up non-docs like configuration files or notes), or the source repo really does have a lot of non-Diátaxis material. Check section 2 (Coverage) and section 6 (Outliers) of REPORT.md and narrow the include globs if needed.

**The agent asks too many questions during the run.**
The skill should only escalate in extreme cases (foundational page + close call + downstream impact). For a typical repo, escalations should fire 0–2 times. If you see many, something is mis-tuned — share the run log so the skill behaviour can be checked.

**The output tree appears in an unexpected location.**
The skill picks an output root in this order: `docs/`, `Docs/`, `documentation/`, `Documentation/`. If one of those exists, the output is `<found>.proposed/`. If none exists, the skill creates `docs/`.

**The run took very long on a big repo.**
The skill is bounded by file count and section count; very large repos (hundreds of files) take proportional time. The agent confirms scope above ~50 files — at that gate, narrow the include globs if needed.

**A re-run produces different verdicts on the same page.**
Verdicts can vary slightly between runs on borderline pages because some judgements (e.g. "substantial", "dominant") are calibrated to the doc set's own baseline. Significant variation usually means a borderline case worth a closer review.

---

## Provenance

This skill is built on the [Diátaxis](https://diataxis.fr/) framework by Daniele Procida. The skill's procedural logic — the four-verdict classification, the rubric for ambiguous cases, the SPLIT mechanics, the rules for grouping vs sub-splitting same-type units — is original to this skill. The conceptual underpinnings (the four types and the action/cognition × acquisition/application axes) are from diataxis.fr; citations are inline in each reference file.
