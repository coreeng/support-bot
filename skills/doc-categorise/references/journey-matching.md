---
name: Journey matching
description: How the skill tags each scanned documentation page with the journey(s) it covers. Load once per run, after classification, before the placement map. Produces journey + variation + confidence tuples per page, used downstream for audience tagging, gap analysis, duplication detection, and suggested actions.
---

# Journey matching

This file specifies how the skill attaches journey relevance to each scanned documentation page. It runs after the Diátaxis classification step and before the placement map is built.

If `journeys = []` was resolved during the input step, **skip this entire step** — every page's relevance list is empty by definition, and no further action is needed.

## Input and output

**Per-page input:**
- The page's title, frontmatter, all headings, and a content sample (≥150 lines, or the full page if shorter).
- The page's Diátaxis verdict from the classification step.

**Per-run input (cached):**
- The resolved `journeys` list from `references/product-definition.md`. Each journey has `name`, optional `description`, `users`, `feature`, `variations`.

**Per-page output:** a list of journey-relevance records.

```yaml
journeys:
  - name: "<exact journey name from the input list>"
    variation: "<variation string from the journey's variations list, or null>"
    confidence: strong | weak
```

The list may be empty (the page covers no listed journey). Ordering: strong matches first, then weak; ties broken by the order journeys appear in the input list.

## Procedure

A two-pass hybrid. Pass 1 is a deterministic surface scan that surfaces candidates cheaply. Pass 2 is an LLM judgement that confirms or rejects each candidate.

### Pass 1 — Deterministic candidate identification

For each page:

1. Tokenise the page's title, first H1, and source filename to lowercase ASCII tokens.
2. For each journey in the input list:
   - Tokenise the journey's `name` and `feature` the same way. Strip stop-words: `a`, `an`, `the`, `of`, `to`, `for`, `with`, `on`, `in`.
   - **Strong candidate** — every remaining `name` token appears in the page's title OR first H1; OR the `feature` token appears in the title/H1 and at least one `name` token appears anywhere in the page sample.
   - **Weak candidate** — at least one `name` token appears anywhere in the page sample, but the strong condition is not met.
   - **Not a candidate** — no token matches.
3. For each candidate, scan the journey's `variations` list:
   - Tokenise each variation string. If exactly one variation's token appears in the page title, H1, or first 50 lines, record that as the matched variation. If multiple variations match, leave variation null and let Pass 2 disambiguate.

A page may have multiple candidate journeys. Record every candidate with its tier (strong/weak) and matched variation (or null).

### Pass 2 — LLM judgement

For each Pass 1 candidate (every tier), evaluate whether the page actually covers the journey. The deterministic pass is intentionally generous — Pass 2 is where false positives are pruned.

The agent is given, for each candidate journey:
- The full journey record (name, description, users, feature, variations).
- The page's title, frontmatter, all headings, and content sample.
- The Pass 1 result (tier and matched variation, if any).

The agent answers, per journey:
- **yes** — the page covers the journey end-to-end or substantively (it would be linked from a journey landing page, or it answers the journey's reader question).
- **partial** — the page touches on the journey but only covers a portion (one step, one variation, or as a side note in a page about something else).
- **no** — the page does not cover this journey despite the surface match.
- The variation(s) actually covered, if any. May correct or override Pass 1's variation guess.
- A one-line reason (kept for diagnostics; not surfaced in the standard report).

Map verdicts to output records:

| Verdict   | Output record                                                          |
| --------- | ---------------------------------------------------------------------- |
| yes       | Include with `confidence: strong`.                                     |
| partial   | Include with `confidence: weak`.                                       |
| no        | Omit this journey from the page's list.                                |

### Pass 3 — Synthesis

For each page, the union of Pass 2's non-"no" results is the page's journey-relevance list.

Variation handling:
- If Pass 2 identified a variation, use it.
- If Pass 2 returned null but Pass 1 had matched a single variation, retain Pass 1's variation only if the journey has variations defined; otherwise null.
- A page covering multiple variations of the same journey is recorded as multiple entries — one per variation — all with the same name and confidence.

## Multiplicity

A page may be relevant to multiple journeys (1:N). Record every match. Downstream steps decide how to handle multi-match pages.

## Canonical journey names

The `name` field in every output record MUST be byte-identical to a `name` in the input journey list. If the LLM produces a variant ("Deployment" vs "Deploy a workload"), canonicalise to the exact input string or discard the match. Never write a name that does not appear in the input list.

## Confidence tiers

Two tiers, no numeric scoring:

- **Strong** — Pass 2 verdict of "yes". Suitable for unhedged statements in stakeholder reports: "this page covers journey X".
- **Weak** — Pass 2 verdict of "partial". Suitable for hedged statements: "this page partially covers journey X — investigate".

A weak match is not a low-confidence guess about whether a match exists — it is a confident statement that the coverage is partial.

These terms appear in multiple places in REPORT.md (counts in the Coverage analysis table, parenthetical counts in the Journey relevance summary, `(weak)` suffixes on per-page journey tags). Per `SKILL.md`'s Executive report format, a "Conventions" blockquote at the top of REPORT.md defines `strong` and `weak` for readers; this reference file is the authoritative source of the definition.

## No-match handling

A page with an empty journey-relevance list is **neutral**. Flag the page in REPORT.md under "Pages with no journey match" but do **not** reclassify the page, override its Diátaxis verdict, or auto-tag it as product-level / outlier. Downstream synthesis (later step) decides whether the page is product-level documentation for builders, scaffolding, or off-strategy.

A page may legitimately have no journey match for several reasons: it is product-level reference or explanation (audience = builders/maintainers, not journey end-users), it is scaffolding, or the supplied journey list is incomplete. The skill does not guess which.

## Output — frontmatter

If a generated output file has at least one journey match, add a `journeys:` field to its frontmatter, in addition to the required `product`, `diataxis_type`, `source_path`:

```yaml
---
product: "Core Platform"
diataxis_type: "how-to"
source_path: "docs/deploy-staging.md"
journeys:
  - name: Deploy a workload
    variation: staging
    confidence: strong
---
```

If the journey-relevance list is empty, **omit** the `journeys:` field entirely. Do not emit `journeys: []`.

The user/feature context from the journey is **not** lifted into the output frontmatter at this stage. The `journeys:` field carries name, variation, and confidence only; downstream consumers resolve user/feature by joining back to the input journey list.

## Output — REPORT.md

Two additions:

### 1. New column on existing tables

Add a "Journeys" column to each of these existing tables:
- Copied verbatim (PERFECT)
- Rewritten (single-type)
- Split

Column value per row:
- Empty list → `—` (em dash).
- One match → `<journey name>` (suffixed `/<variation>` if variation is set, and `(weak)` after weak matches). Example: `Deploy a workload/staging (weak)`.
- Multiple matches → comma-separated; strong listed first.

The Outliers table does **not** get a Journeys column — outliers are pages that don't fit any Diátaxis type, and journey relevance for them is meaningless.

### 2. New section: "Journey relevance summary"

Placed in the exec block of REPORT.md, immediately after Coverage analysis and before Duplication candidates. See `SKILL.md`'s Executive report format for the full section ordering.

The section contains two parts:

**Part A — Per-journey page count table.** One row per journey from the input list. Columns: journey name, tutorial count, how-to count, reference count, explanation count, total. The count includes both strong and weak matches; weak counts may optionally be shown in parentheses, e.g. `5 (2 weak)`.

**Part B — Pages with no journey match.** A table listing every scanned page that produced an empty journey-relevance list. Columns: source path, assigned Diátaxis type, a brief content hint (the first non-empty H1 or the first non-empty paragraph, truncated to **~50 characters** — long enough to identify the page, short enough not to dominate the table). Source paths in this table benefit from the report-wide prefix-stripping rule in `SKILL.md` when applicable.

The journey relevance summary is independent of "Coverage by source folder" — the two tables answer different questions (one is by-folder source organisation, the other is by-journey coverage).

## Scope of this step

This step:
- Tags each page with journey relevance (name + variation + confidence).
- Writes the relevance to output frontmatter and REPORT.md.

It does NOT:
- Compute gap analysis ("does journey X have any how-to coverage?"). That is a later step.
- Tag audience or user. That is a later step.
- Detect duplication. That is a later step.
- Suggest actions. That is a later step.
- Modify the Diátaxis verdict produced by the classification step.

## Sources

The deterministic-then-LLM hybrid is original to this skill. The principle (deterministic surface scan to cheaply enumerate candidates, semantic judgement to confirm) is a common pattern in retrieval and information extraction; here it is applied to documentation-to-journey relevance.
