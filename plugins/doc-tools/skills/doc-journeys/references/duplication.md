---
name: Duplication detection
description: How the skill identifies candidate duplicate clusters — groups of pages sharing the same (journey, Diátaxis type, variation) tuple, plus cross-product pages that restate a page they route to. Load once per run, after gap analysis, before the placement map. Intentionally simple: no LLM judgement, no semantic similarity, structural comparison only. Read the "What this does NOT catch" section before interpreting the output.
---

# Duplication detection

This file specifies how the skill identifies candidate duplicate clusters. It runs after gap analysis (`${CLAUDE_SKILL_DIR}/references/gap-analysis.md`) and before the placement map.

This step is **intentionally minimal**. It uses one structural rule, no LLM judgement, no semantic similarity. Read the "What this does NOT catch" section carefully before interpreting the output — the simplicity comes at a cost, and the cost is explicit by design.

## The rules

There are two, and they catch different things.

> **Rule 1 — shared tuple.** A **duplicate candidate cluster** is any group of two or more pages that share the same `(journey, Diátaxis type, variation)` tuple, where the journey match is strong.

> **Rule 2 — restated route.** A **restating cross-product page** is one that reproduces a literal — a command, path, config key, or resource name — that also appears on a page it links to via `routes_to`.

Both are structural. There is no scoring, no similarity threshold, no LLM confirmation.

Rule 1 requires a **shared journey**, so it cannot see the overlap between the two journey sections — those are by construction different journeys. Rule 2 exists for exactly that blind spot, and it is cheap because `routes_to` already names which pairs to compare: there is no N² sweep, only one comparison per declared route.

## Why "candidates" and not "duplicates"

Sharing the same `(journey, Diátaxis type, variation)` tuple is a strong structural signal that two pages are likely covering the same ground, but it is not a guarantee. Two how-tos for "deploy a workload / linux" may be:

- Genuine duplicates (one written years ago, one written recently, neither author found the other) — the common rot pattern this step is designed to catch.
- Complementary pages addressing different sub-aspects of the same goal — legitimately separate.
- Variations the journey definition didn't capture (e.g. one is for v1 of the platform, one for v2).

The skill cannot tell which without reading the content. So it surfaces the cluster as a **candidate for consolidation** and lets the stakeholder decide per cluster.

A small number of false positives at this stage is cheaper than the alternative (missing real duplicates because the bar to flag was too high).

## Procedure — Rule 1 (shared tuple)

### Step 1 — Filter scope

Consider only pages that have at least one journey match with `confidence: strong` (from `${CLAUDE_SKILL_DIR}/references/journey-matching.md`). Pages with only weak journey matches are excluded — weak matches are partial coverage by definition, and including them would generate false positives.

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

## Procedure — Rule 2 (restated route)

Runs only when the run authored cross-product journey pages. For each such page:

### Step 1 — Resolve the routed set

From the page's `doc_journeys.routes_to`, collect every target page that exists on disk. Add any page the body links to under the output root or any `prior_art_roots` entry, whether or not it was declared — a page linked but not declared is still a page being routed to.

### Step 2 — Extract literals from both sides

From the cross-product page and from each target, extract:

  * the contents of every fenced code block
  * inline-code spans matching a command, file path, config key, resource name, or flag

These are the things `${CLAUDE_SKILL_DIR}/references/authoring.md` requires be copied character-for-character, which is what makes them comparable at all: a restated step reproduces them exactly, because paraphrasing them is already forbidden.

### Step 3 — Flag overlaps

Any literal appearing on both sides is a **restating flag**. Record the literal, the cross-product page, and the target page it duplicates.

Ignore literals shorter than 8 characters and any that appear on more than three pages across the run — a bare `kubectl` or a namespace name is vocabulary, not a restated step.

### Step 4 — Emit

```yaml
- page: "<cross-product page path>"
  target: "<routed-to page path>"
  literals: ["<literal 1>", "<literal 2>", ...]
  count: N
```

### What a flag means, and what it does not

A restating flag is a **defect in a page this run authored**, not a reportable observation about legacy content. Rule 1 against prior art is tolerated because two teams writing the same thing years apart is a fact about the estate; Rule 2 firing means the skill wrote a route that duplicates its own destination in the same run, and `${CLAUDE_SKILL_DIR}/references/authoring.md` is explicit that it must not.

The remedy is to cut the restated material from the route and leave the link, not to delete the page and not to edit the target. Apply it before writing, and report the flag either way so a reviewer can see the route was trimmed rather than assume it was always clean.

One exception: a literal the reader needs **in order to choose between routes** — a resource type name that tells them which path applies — is orientation, not restatement. Keep it and record why in the report.

## What this catches

Rule 1 catches the common rot pattern: two how-tos for the same journey + same variation, both still in the docs tree, neither author having found the other. This is the highest-frequency duplication problem in legacy docs and is what this version optimises for.

Rule 2 catches the failure mode the two-section layout introduces: a wayfinding page that quietly grew into a second copy of the procedure it was supposed to point at. That page competes with its own destination, and because it links to it, a reader has no way to tell which is authoritative.

## What this does NOT catch (out of scope for this version)

The following are deliberately out of scope. Each is documented here so stakeholders reading the report know what the absence of a cluster does **not** prove.

- **Pages with no journey tag.** Product-level documentation (reference, explanation, how-tos for builders/maintainers) is not analysed for duplication. Two reference pages for the same component written years apart would not be flagged.

  **What a reviewer must therefore not do.** Neither rule fires on a product-only run, so no remedy in this file is available for one — in particular `replace-with-link`, which is Rule 2's remedy and belongs to cross-product journey pages that declare `routes_to`. A reviewer that reaches for it anyway is applying a remedy no rule licensed, and the failure is not theoretical: on one run it was proposed for a product-level explanation/reference pair, where the cut would have deleted framing the reviewer's own analysis had listed as material that must survive. **On a product-only run, report overlap as an observation and propose a cross-link. Never propose a deletion.**

  The reason the remedy inverts is worth stating. Rule 2's deletion is safe because the destination is *linked* — the reader loses nothing, because the route still points at the canonical copy. Product-level overlap usually has no link at all, so the real defect is the *missing cross-link*, and deleting content on top of it removes correct material to fix an absence. Where a page declares `overlap: full` against prior art and no page body links it, that is a missing-link finding, not a surplus-content one.
- **Cross-journey duplicates, except along a declared route.** Two pages saying roughly the same thing but tagged to different journeys will not cluster under Rule 1, which requires a shared journey. Rule 2 covers one specific case — a cross-product page and a page it routes to — and nothing else. Two *product* journeys overlapping, or a cross-product journey overlapping one it does not link to, are both invisible here.

  This gap has teeth given the current journey set. "Make the service production-ready" and "Configure the application for production" are the same ground under different names in different sections, and unless one declares a route to the other, neither rule fires. **A clean duplication section is not evidence that the journey set is non-overlapping** — it is evidence that no two pages shared a tuple and no route restated its destination.
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

3. A **Restated routes** subsection for Rule 2, present whenever the run authored cross-product pages. Columns:
   - **Cross-product page** — path.
   - **Restates** — the routed-to page path.
   - **Literals** — the duplicated strings, one per line.
   - **Action** — `trimmed` where the material was cut before writing, `kept — orientation` for the choose-between-routes exception, with the reason.

   If no flags fired, the subsection contains the single line "No cross-product page restated a page it routes to." State it explicitly; the absence of the subsection would be indistinguishable from the check not having run.

## What this step does not do

- It does not confirm clusters are actually duplicates — see "Why 'candidates' and not 'duplicates'".
- It does not flag unclear, hollow, or contradictory content — that is the next step.
- It does not suggest a canonical page or any merge action — that is the synthesis step.
- It does not modify per-page outputs.
- It does not analyse pages without journey tags.

## Sources

The `(journey, Diátaxis type, variation)` clustering rule is original to this skill. The intentional minimalism — surface candidates, let humans confirm — is a deliberate choice to keep the v1 behaviour reproducible and cheap.
