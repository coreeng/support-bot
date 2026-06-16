---
name: Weighting analysis
description: Optional ideal-vs-actual weighting comparison. Ingests a per-journey ideal weighting table from `product-definition/weightings.md`, computes word-count-based actuals per journey and per Diátaxis type within each journey, and extends the "Journey relevance summary" Part A with three nested tables. Load after gap analysis, before duplication detection. Purely descriptive — no suggested actions are emitted.
---

# Weighting analysis

This step is **optional**. If `product-definition/weightings.md` is absent, skip the step entirely; Part A retains its existing structure (Part A1 only). When present, the file declares the **ideal** content distribution for each journey, and the skill computes word-count-based **actuals** for comparison.

The step is purely descriptive: no suggested actions are emitted from weighting deltas. The reader judges what the deltas mean for their product.

## Input — `product-definition/weightings.md`

Located alongside `product.md` and `journeys/` under `<repo_root>/product-definition/`. The skill is read-only with respect to this file.

### Schema

The file body MUST contain exactly one GitHub-flavoured markdown table with these columns (header matching is case-insensitive; column order is not significant):

| Column                  | Required | Type        | Notes                                                                                            |
| ----------------------- | -------- | ----------- | ------------------------------------------------------------------------------------------------ |
| `Journey`               | Yes      | string      | Must match a `name` in `product-definition/journeys/`. Reconciliation is case- and whitespace-insensitive. |
| `Implementation order`  | No       | integer     | Informational. Not consumed by any calculation; not displayed in the report.                     |
| `Journey weight`        | Yes      | percentage  | Ideal share of total content for this journey. Accepts `4%`, `4`, or `0.04`.                     |
| `Min topics`            | No       | integer     | Informational lower bound for descriptive reporting. Not enforced.                                |
| `Max topics`            | No       | integer     | Informational upper bound for descriptive reporting. Not enforced.                                |
| `Explanation`           | Yes      | percentage  | Ideal share of *this journey's* content that is the Explanation Diátaxis type.                   |
| `How-to`                | Yes      | percentage  | As above for How-to.                                                                              |
| `Reference`             | Yes      | percentage  | As above for Reference.                                                                           |
| `Tutorial`              | Yes      | percentage  | As above for Tutorial.                                                                            |

Frontmatter on the file is allowed but ignored.

### Validation

1. Every `Journey` cell MUST reconcile to a known journey (case- and whitespace-insensitive equality against `journeys/*.md` `name` fields). Unreconciled rows are **skipped** and listed in the run summary under "Skipped weighting rows".
2. Journeys present in `journeys/` but absent from the weightings table get no ideal columns; their Part A2 row shows `—` in the Ideal/Δ columns and is listed under "Journeys without ideal weighting" in the run summary.
3. The sum of `Journey weight` across reconciled rows SHOULD be 100% (±1pp tolerance for rounding). Out-of-tolerance sums are accepted but flagged in the run summary as "Weighting sum out of tolerance: actual N%". Never auto-normalise.
4. For each row, the sum of `Explanation + How-to + Reference + Tutorial` SHOULD be 100% (±1pp). Out-of-tolerance rows are accepted but flagged.
5. On any structural error (no table, missing required column, non-numeric percentage), skip the file entirely with a single run-summary line: "Weighting file present but unparseable; weighting analysis skipped." Do not fall through to a paste prompt; the comparison is optional, not required.

## Word counting

The "actual" measure is **word count** per page, computed once per scanned page after Diátaxis classification.

Procedure:
1. Strip YAML frontmatter (everything between the opening and closing `---` fences, inclusive).
2. Strip fenced code blocks (lines between matching ``` `` ` `` `` fences, inclusive).
3. Strip HTML comments (`<!-- ... -->`, including multi-line).
4. Strip Markdown link URLs but keep link text: `[text](url)` → `text`. Same for autolinks `<https://…>` → removed.
5. Keep image alt text: `![alt](url)` → `alt`. Drop the URL.
6. Strip remaining Markdown punctuation that is not part of a word (`#`, `*`, `_`, `>`, `|`, `-` when leading a list item, table pipes, etc.).
7. Count whitespace-separated tokens on what remains. The integer count is the page's `word_count`.

SPLIT outputs are counted at the **source-page** level, not per output file: a single source page contributes one `word_count` value to whichever journey(s) it matches, regardless of how many output files it produces.

## Apportionment

A page may match multiple journeys (strong or weak). To keep per-journey actual shares summing to 100%, each page's word count is **apportioned** equally across its journey matches.

For page P with word count W and N journey matches (strong + weak combined, both tiers count as 1 match), each matched journey receives `W / N` words from P. Apportionment runs across the union of strong and weak matches; no tier weighting.

Pages with zero journey matches are excluded from the denominator (they appear in Part B "Pages with no journey match", not Part A2/A3).

## Calculations

Let `M(J)` = set of pages with at least one match (strong or weak) for journey J. Let `M(J, T)` = subset of `M(J)` with Diátaxis type T. Let `apportioned(P, J)` = `word_count(P) / N_matches(P)`.

- **Journey total apportioned words:** `W(J) = Σ apportioned(P, J) for P in M(J)`
- **Grand apportioned total:** `W_total = Σ W(J) for J in journeys with weightings`
- **Actual journey share:** `actual_pct(J) = W(J) / W_total × 100`
- **Type apportioned words within journey:** `W(J, T) = Σ apportioned(P, J) for P in M(J, T)`
- **Actual type mix within journey:** `actual_type_pct(J, T) = W(J, T) / W(J) × 100`
- **Δ values:** `actual − ideal`, signed, in percentage points, rounded to nearest integer for display.
- **Actual topics:** `|M(J)|` — count of distinct source pages matched to journey J at any confidence.

Journeys present in `journeys/` but absent from the weightings table are **excluded from `W_total`** so the comparable journeys still sum to 100%.

## Output — REPORT.md

This file contributes Parts **A2** and **A3** under the "Journey relevance summary" Part A heading. Part A1 (existing), Part A4 (topic coverage, specified in `references/topic-coverage.md`), and Part A5 (per-journey page index, specified in `references/journey-matching.md`) are out of scope for this file but MUST appear in the report alongside A2/A3 when their own emit conditions are met. The full Part A sequence is **A1 → A2 → A3 → A4 → A5**. Part B ("Pages with no journey match") is unchanged.

### Part A1 — Per-journey page counts *(unchanged)*

Existing table: `Journey | Tutorial | How-to | Reference | Explanation | Total`. Retained as ground truth for the cross-section consistency invariants in SKILL.md.

### Part A2 — Per-journey weighting comparison *(new, only when weightings file resolved)*

Columns: `Journey | Ideal % | Actual % | Δ (pp) | Min topics | Max topics | Actual topics`.

One row per reconciled journey, in input-list order. Journeys without an ideal entry are appended at the bottom with `—` in Ideal/Δ/Min/Max cells.

### Part A3 — Per-type mix within each journey *(new, only when weightings file resolved)*

Columns: `Journey | Type | Ideal % | Actual % | Δ (pp)`.

Four rows per reconciled journey (Explanation, How-to, Reference, Tutorial — in that order), grouped by journey in input-list order. Journeys without an ideal entry are omitted from A3 entirely.

When the weightings file is absent or unparseable, A2 and A3 are not emitted; Part A consists of A1 only and an italic line follows it: `*No weighting file found at `product-definition/weightings.md`; ideal-vs-actual comparison skipped.*`

## What this does NOT do

- **Does not weight by tier.** Strong and weak matches contribute equally to apportionment. A weak match still counts as one journey claim on the page's words. To exclude weak matches from the comparison, edit `weightings.md` is the wrong remedy — change the apportionment rule here.
- **Does not enforce min/max topics.** Those columns are displayed for the reader's reference; no warning, action, or verdict is derived from them.
- **Does not normalise sums.** Out-of-tolerance ideal sums are reported and used as-is.
- **Does not handle SPLIT outputs as separate units.** Word count is measured at the source page; per-output-file word counts are not computed.
- **Does not consider audience tier.** A page matched to journey J counts toward J regardless of whether its audience is `end-user` or `builder/maintainer`. Audience mismatches are reported separately per `references/audience-tagging.md`.

## Cross-section consistency

The per-page word-count values and per-page journey-match lists are the ground truth for Part A2 and A3. The cross-section consistency invariants in SKILL.md are extended with a fifth item covering this: actuals MUST be derivable from per-page tags + word counts; aggregates MUST NOT re-evaluate matches or recompute word counts on a sub-selection.
