---
name: Audience tagging
description: How the skill tags each scanned page with an audience tier (builder/maintainer vs end-user) and a detailed audience label list. Load once per run, after journey matching, before the placement map. Produces tier, labels, confidence, source, and any audience mismatches per page.
---

# Audience tagging

This file specifies how the skill attaches audience signals to each scanned documentation page. It runs after journey matching (`references/journey-matching.md`) and before the placement map is built.

Unlike journey matching, audience tagging **always runs**. When `journeys = []` or a page has no journey match, the tier defaults to `builder/maintainer` and detailed labels are inferred from content. The "Audience mismatches" subsection of REPORT.md will simply be empty when there are no journey-supplied labels to compare against.

## Input and output

**Per-page input:**
- The page's title, frontmatter, all headings, and content sample.
- The page's Diátaxis verdict.
- The page's journey-relevance list from `references/journey-matching.md` (possibly empty).

**Per-run input (cached):**
- The resolved `journeys` list, including each journey's `users:` field.
- The **preferred vocabulary**: the union of every `users:` value across all journeys, deduped, case-insensitive.

**Per-page output:**

```yaml
audience:
  tier: builder/maintainer | end-user
  labels: ["<label>", ...]
  confidence: strong | weak
  source: journey | inferred | both
  mismatch:                          # only when a mismatch was detected
    inferred_labels: [...]
    journey_labels: [...]
    inferred_confidence: strong | weak
```

`labels` may be empty when content provides no audience signal at all; in that case `confidence: weak`, `source: inferred`.

## Tier vocabulary

A fixed binary: `builder/maintainer` or `end-user`. Exact strings (including the slash). Taken from the team's "good docs" definition:

- **Product-level documentation** (reference, explanation, how-to aimed at product builders/maintainers) → `builder/maintainer`.
- **Journey-level documentation** (end-to-end how-to aimed at end-users) → `end-user`.

The tier is derived structurally from the journey-relevance list — see Step 1 below.

## Detailed label vocabulary

Free-form. The preferred vocabulary is the union of `users:` values across the input journey list (e.g. `[end-user, application-developer, platform-engineer, sre]` if those appear across the journeys). The LLM is instructed to reuse exact strings from this vocabulary when inferring audience; new labels are allowed only when no vocabulary entry fits.

## Procedure

### Step 1 — Determine tier

Derived directly from the page's journey-relevance list:

- If the list has at least one match (strong OR weak) → `tier = end-user`.
- Otherwise → `tier = builder/maintainer`.

The tier is not LLM-judged; it falls out of Step 2's results.

### Step 2 — Determine detailed labels

Three branches, mutually exclusive:

**Branch A — page has journey matches AND at least one matched journey has a non-empty `users:` field.**

1. Collect the union of `users:` from every matched journey, dedupe preserving first-seen order.
2. Record:
   - `labels` = the union.
   - `source` = `journey`.
   - `confidence` = `strong` (authored intent).
3. Also run the Step 3 inference for mismatch detection (does not overwrite `labels`).

**Branch B — page has no journey matches.**

1. Run the inference described below.
2. Record:
   - `labels` = inferred labels.
   - `source` = `inferred`.
   - `confidence` = `strong` or `weak` per LLM judgement.

**Branch C — page has journey matches BUT every matched journey has an empty `users:` field.**

1. Run the inference described below.
2. Record:
   - `labels` = inferred labels.
   - `source` = `inferred`.
   - `confidence` = `strong` or `weak` per LLM judgement.

### Inference procedure (used by Branches B and C, and by Step 3 mismatch detection)

The LLM is given:
- The page's title, frontmatter, all headings, and content sample.
- The preferred vocabulary (so the LLM can reuse existing labels rather than invent new ones).

The LLM produces:
- A list of one or more audience labels. Each label is either a verbatim string from the preferred vocabulary, or a new free-form label if no vocabulary entry fits. The list may be empty when no audience signal is detectable.
- A confidence:
  - **strong** — page contains explicit audience tells ("for SREs", "if you're a platform engineer", "assumes Kubernetes experience"), prerequisite literacy declarations, or unambiguous voice cues that pin the audience.
  - **weak** — audience inferred only from indirect signals (tone, vocabulary, framing) without explicit statements.

If the LLM cannot produce even a weak inference, `labels = []` and `confidence = weak`.

### Step 3 — Mismatch detection (Branch A pages only)

For pages that went through Branch A, also run the inference procedure. Compare:

- Branch A labels (journey-supplied, authoritative).
- Inferred labels.

A **mismatch** exists if the symmetric difference of the two label sets (case-insensitive) is non-empty.

When a mismatch exists:
- Keep `labels` as the Branch A (journey-supplied) labels — authored intent wins.
- Record the mismatch block:
  - `inferred_labels` — what the content reads like.
  - `journey_labels` — what the journey says.
  - `inferred_confidence` — confidence from the inference pass.
- `source` stays `journey`.

When there is no mismatch AND the inference confidence is `strong`:
- Promote `source` from `journey` to `both` — the inferred audience confirmed the authored intent.

When there is no mismatch but the inference confidence is `weak`:
- `source` stays `journey`. Weak inference is not strong enough to count as confirmation.

A mismatch is **not an error**. It is a signal that the documentation's voice or framing has drifted from the journey's intended audience — actionable evidence for the stakeholder discussion.

## Output — frontmatter

Every output file gains an `audience:` block in its frontmatter, regardless of journey match status. Pages with no journey match still get tier `builder/maintainer` and inferred labels.

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

Example with a mismatch:

```yaml
audience:
  tier: end-user
  labels: [end-user]
  confidence: strong
  source: journey
  mismatch:
    inferred_labels: [platform-engineer]
    journey_labels: [end-user]
    inferred_confidence: strong
```

Example for a page with no journey match:

```yaml
audience:
  tier: builder/maintainer
  labels: [platform-engineer, sre]
  confidence: strong
  source: inferred
```

## Output — REPORT.md

Three changes:

### 1. Tier counts in Summary

Add one line to the Summary section of REPORT.md: `Audience tiers: end-user X, builder/maintainer Y.`

### 2. New "Audience" column on per-page tables

Add an Audience column to the PERFECT, Rewritten, and Split tables. Column format: `<tier> · <labels-comma-separated>`. Examples:

- `end-user · end-user, application-developer`
- `builder/maintainer · platform-engineer (weak)`
- `end-user · end-user (mismatch: platform-engineer)` — a brief mismatch hint inline; full detail in the mismatches subsection.

The Outliers table does NOT get an Audience column.

### 3. New "Audience mismatches" subsection inside the Journey relevance summary section

A table listing every page where a mismatch was detected. Columns:

- Source path.
- Matched journey name(s).
- Authored audience (from journey).
- Inferred audience (from content).
- Inferred confidence (strong/weak).

The mismatch table is the most actionable artefact of this step — it surfaces drift between authored journey audience and what the documentation actually reads like.

If no mismatches were detected, the subsection still appears with a single line: "No audience mismatches detected." This makes the absence explicit rather than ambiguous.

## What this step does not do

- It does not compute gap analysis. That is the next step.
- It does not detect duplication.
- It does not suggest actions.
- It does not modify Diátaxis verdicts or journey-relevance results.
- It does not edit the source documents.

## Sources

The binary tier vocabulary (`builder/maintainer` vs `end-user`) is from the team's "good docs" definition. The free-form detailed label inference and the mismatch-detection procedure are original to this skill.
