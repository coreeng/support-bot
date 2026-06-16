---
name: Duplication detection
description: How the skill identifies candidate duplicate clusters — groups of pages sharing the same (journey, Diátaxis type, variation) tuple. Load once per run, after gap analysis, before the placement map. Intentionally simple: no LLM judgement, no semantic similarity, structural grouping only. Read the "What this does NOT catch" section before interpreting the output.
---

# Duplication detection

This file specifies how the skill identifies candidate duplicate clusters. It runs after gap analysis (`references/gap-analysis.md`) and before the placement map.

This step is **intentionally minimal**. It uses one structural rule, no LLM judgement, no semantic similarity. Read the "What this does NOT catch" section carefully before interpreting the output — the simplicity comes at a cost, and the cost is explicit by design.

## The rule

> A **duplicate candidate cluster** is any group of two or more pages that share the same `(journey, Diátaxis type, variation)` tuple, where the journey match is strong.

That is the entire detection logic. There is no scoring, no similarity threshold, no LLM confirmation.

## Why "candidates" and not "duplicates"

Sharing the same `(journey, Diátaxis type, variation)` tuple is a strong structural signal that two pages are likely covering the same ground, but it is not a guarantee. Two how-tos for "deploy a workload / linux" may be:

- Genuine duplicates (one written years ago, one written recently, neither author found the other) — the common rot pattern this step is designed to catch.
- Complementary pages addressing different sub-aspects of the same goal — legitimately separate.
- Variations the journey definition didn't capture (e.g. one is for v1 of the platform, one for v2).

The skill cannot tell which without reading the content. So it surfaces the cluster as a **candidate for consolidation** and lets the stakeholder decide per cluster.

A small number of false positives at this stage is cheaper than the alternative (missing real duplicates because the bar to flag was too high).

## Procedure

### Step 1 — Filter scope

Consider only pages that have at least one journey match with `confidence: strong` (from `references/journey-matching.md`). Pages with only weak journey matches are excluded — weak matches are partial coverage by definition, and including them would generate false positives.

Pages with no journey match are **not analysed** for duplication in this version. See "What this does NOT catch" below.

### Step 2 — Build tuples

For each in-scope page, enumerate its `(journey, Diátaxis type, variation)` tuples. A page with N strong journey matches contributes N tuples (one per match). Note:

- `Diátaxis type` is the page's classification verdict, lowered to one of `tutorial`, `how-to`, `reference`, `explanation`. SPLIT outputs use each split output's individual type.
- `variation` is taken from the journey-match record. May be `null` if the journey has no variations or the variation wasn't resolved.
- Two tuples match only if all three components match. A page with `variation: null` does NOT cluster with a page with `variation: linux` for the same journey + type.

### Step 3 — Group by tuple

Group pages by tuple. A cluster exists for any tuple shared by two or more pages.

A page can appear in **multiple clusters** if it has multiple strong journey matches that each share a tuple with other pages. This is not noise — it is informative: the page covers multiple journeys, and other pages cover each of those journeys too.

### Step 4 — Emit clusters

For each cluster, record:

```yaml
- journey: "<journey name>"
  diataxis_type: "<type>"
  variation: "<variation or null>"
  pages: ["<source_path 1>", "<source_path 2>", ...]
  size: N
```

Sort clusters by size (largest first), then by journey name. Clusters of size 1 do **not** exist by definition — a single page is not a cluster.

## What this catches

The common rot pattern: two how-tos for the same journey + same variation, both still in the docs tree, neither author having found the other. This is the highest-frequency duplication problem in legacy docs and is what this version optimises for.

## What this does NOT catch (out of scope for this version)

The following are deliberately out of scope. Each is documented here so stakeholders reading the report know what the absence of a cluster does **not** prove.

- **Pages with no journey tag.** Product-level documentation (reference, explanation, how-tos for builders/maintainers) is not analysed for duplication. Two reference pages for the same component written years apart would not be flagged.
- **Cross-journey duplicates.** Two pages saying roughly the same thing, but tagged to different journeys, will not cluster. The structural rule requires a shared journey.
- **Cross-type duplicates.** A reference doc and an explanation doc that drift into saying the same thing will not cluster — they have different Diátaxis types.
- **Semantic similarity across variations.** A how-to for "deploy / linux" and a how-to for "deploy / macos" will not cluster (different variations). This is intentional — variants of one goal are legitimately separate.
- **Pages with only weak journey matches.** Weak matches indicate partial coverage; clustering them would create false positives.
- **Near-identical content with different journey tags.** Same as cross-journey.

If any of these matter, they require LLM-based semantic similarity detection. That is a future-version feature and not in this step.

## Output — REPORT.md

A new section "Duplication candidates", placed in the exec block of REPORT.md immediately after "Journey relevance summary" and before "Quality flags". See `SKILL.md`'s Executive report format for the full section ordering.

The section header is followed by:

1. A short reminder of what the rule catches and what it does not — one or two sentences, so stakeholders reading the report in isolation understand the scope before reading the table.
2. The cluster table. Columns:
   - **Journey** — name.
   - **Type** — Diátaxis type.
   - **Variation** — variation string or `—`.
   - **Size** — number of pages in the cluster.
   - **Pages** — bulleted list of source paths (one per line within the cell, or a comma-separated list for compact rendering).

Sort: largest cluster first; ties by journey name in input order.

If no clusters were found, the section contains the single line "No duplicate candidates detected by the (journey, type, variation) rule." Do not omit the section.

If `journeys` was empty for the run, the section contains the single line "Duplication detection requires a journey list; none was supplied." Do not omit the section.

## What this step does not do

- It does not confirm clusters are actually duplicates — see "Why 'candidates' and not 'duplicates'".
- It does not flag unclear, hollow, or contradictory content — that is the next step.
- It does not suggest a canonical page or any merge action — that is the synthesis step.
- It does not modify per-page outputs.
- It does not analyse pages without journey tags.

## Sources

The `(journey, Diátaxis type, variation)` clustering rule is original to this skill. The intentional minimalism — surface candidates, let humans confirm — is a deliberate choice to keep the v1 behaviour reproducible and cheap.
