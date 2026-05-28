---
name: Gap analysis
description: How the skill computes coverage gaps — per-journey verdicts (covered/partial/missing) with reasons, plus product-level R/E/H presence checks. Load once per run, after audience tagging, before the placement map. Produces a coverage report for stakeholder discussion.
---

# Gap analysis

This file specifies how the skill computes documentation coverage gaps. It runs after audience tagging (`references/audience-tagging.md`) and before the placement map is built.

The output is a coverage analysis written into REPORT.md. Gap analysis is per-journey and per-product; it does **not** modify per-page frontmatter or any other per-page output.

## Input and output

**Per-run inputs (already computed by earlier steps):**
- The resolved `journeys` list (each journey has name, optional users, feature, variations).
- For every scanned page: the Diátaxis verdict, the journey-relevance list, the audience block.

**Per-run outputs:**
- **Journey coverage** — one record per supplied journey with `verdict`, reasons, variation status, and counts.
- **Product-level coverage** — counts of builder/maintainer-audience pages by Diátaxis type, plus flags for any of R/E/H at zero.

## Procedure

### Part A — Journey coverage

Run only if `journeys` is non-empty. For each journey:

#### Step 1 — Collect matched pages

Collect every page whose journey-relevance list contains an entry for this journey. Bucket by Diátaxis type and confidence:

- `strong_how_to` — page's Diátaxis verdict is PERFECT-how-to, REWRITE-how-to, or any how-to output of a SPLIT, AND the journey match for this journey has `confidence: strong`.
- `weak_how_to` — same Diátaxis criteria, but `confidence: weak`.
- `non_how_to` — matched pages whose Diátaxis type is tutorial, reference, or explanation (any confidence).

#### Step 2 — Variation coverage

If the journey has a non-empty `variations` list:

For each variation:
- A variation is **covered** if at least one matched page has a journey-match record with `variation` equal to this variation string (case-insensitive).
- Otherwise the variation is **missing**.

If the journey has no variations, skip Step 2; the journey is treated as a single unit.

#### Step 3 — End-to-end check

Run only when `strong_how_to` is non-empty. Skip otherwise — a journey with no strong how-to is already partial or missing and the check would not change the verdict.

The agent is given:
- The journey's name, description, and variations.
- The content sample of every page in `strong_how_to`, concatenated and capped at ~3000 lines total. Prioritise pages in input order; if the cap is hit, prefer headings + opening paragraphs of each page over full bodies.

The agent answers:
- `end_to_end: yes` — the matched how-tos collectively walk the reader from start to finish of the journey, covering every essential step.
- `end_to_end: no` — one or more essential steps are missing. Provide one short sentence naming the missing step(s).

#### Step 4 — Determine verdict

Apply the following rules in order:

1. `strong_how_to` empty AND `weak_how_to` empty AND `non_how_to` empty → **missing**. Reason: `no matching pages`.
2. `strong_how_to` empty AND `weak_how_to` empty AND `non_how_to` non-empty → **partial**. Reason: `no how-to; only non-how-to types matched: [comma-separated type list]`.
3. `strong_how_to` empty AND `weak_how_to` non-empty → **partial**. Reason: `weak how-to matches only — investigate`.
4. `strong_how_to` non-empty:
   - Compute variation gaps from Step 2: a list of missing variations.
   - Read the end-to-end result from Step 3.
   - If variation gaps OR `end_to_end: no` → **partial**. Reasons: `missing variations: [list]` and/or `not end-to-end: [missing-step sentence]`.
   - Else → **covered**. No reasons.

#### Step 5 — Record

Per journey:

```yaml
name: "<journey name>"
verdict: covered | partial | missing
reasons: ["<reason 1>", "<reason 2>"]    # empty list when verdict is "covered"
strong_how_to_count: N
weak_how_to_count: N
non_how_to_count: N
variations:                              # absent when journey has no variations
  - { name: "<variation>", covered: true | false }
end_to_end: yes | no | n/a               # "n/a" when the check was not run
```

### Part B — Product-level coverage

Always runs, regardless of whether `journeys` is empty. Per the team's "good docs" definition, product-level documentation comprises reference, explanation, and how-to types aimed at builders/maintainers.

#### Step 1 — Count

Count every scanned page whose `audience.tier = builder/maintainer`, grouped by Diátaxis type:
- `reference_count`
- `explanation_count`
- `how_to_count`
- `tutorial_count` (informational only — tutorials are not part of the team's product-level expectation, but reported for completeness)

Outliers do not count (they are not audience-tagged).

#### Step 2 — Flag

For each of the three core types (R/E/H), emit a flag if the count is zero:
- "No reference documentation for builders/maintainers detected."
- "No explanation documentation for builders/maintainers detected."
- "No how-to documentation for builders/maintainers detected."

If all three are non-zero, the flag list is empty.

#### Step 3 — Record

```yaml
builder_maintainer:
  reference_count: N
  explanation_count: N
  how_to_count: N
  tutorial_count: N
  flags: ["<flag 1>", ...]
```

## What `partial` means

`partial` is a catch-all for "exists but not adequate." The reasons list makes the inadequacy specific. A journey may have multiple reasons concurrently (e.g. `missing variations: macos, windows` AND `not end-to-end: rollback step is not documented`). Reasons surface the actionable gap, not just the verdict.

## When `journeys` is empty

Part A is skipped entirely. Part B always runs. The REPORT.md section still appears (see below); Subsection A's table is replaced with the line "No journeys were supplied for this run."

## Output — REPORT.md

A new section, "Coverage analysis", placed in the exec block of REPORT.md immediately after "Suggested actions" and before "Journey relevance summary". See `SKILL.md`'s Executive report format for the full section ordering. Two subsections:

### Subsection A — Journey coverage

A table with one row per supplied journey. Columns:

- **Journey** — name.
- **Verdict** — `covered` / `partial` / `missing`, prefixed by a stoplight emoji for at-a-glance scanning: 🟢 `covered`, 🟡 `partial`, 🔴 `missing`. The emoji is always present; cell contents are e.g. `🔴 missing`, `🟡 partial`, `🟢 covered`.
- **How-to coverage** — `X strong, Y weak`.
- **Other types** — comma-separated counts where non-zero, e.g. `2 reference, 1 explanation`; `—` if none.
- **Variations** — for journeys with variations, a single cell with each variation marked `✓` or `✗`, e.g. `linux ✓ · macos ✗ · windows ✓`. `—` if no variations.
- **Reasons** — comma-separated reasons; empty for `covered`.

Sort: `missing` first, then `partial`, then `covered`. Within each verdict, preserve the order journeys appear in the input.

### Subsection B — Product-level coverage

A small block showing builder/maintainer audience page counts by Diátaxis type, followed by flags:

```
Builder/maintainer audience pages:
- Reference: 12
- Explanation: 5
- How-to: 8
- Tutorial: 2

Flags:
- (none)
```

If any flag fires, list each flag on its own line. If no flags, show `(none)` so the absence is explicit.

## What this step does not do

- It does not detect duplication — that is the next step.
- It does not flag unclear, hollow, contradictory, or factually incorrect content — out of scope here.
- It does not suggest actions — that is the synthesis step.
- It does not modify per-page outputs or any earlier output.
- It does not classify or re-classify pages.

## Sources

The journey-coverage verdicts and variation-by-variation breakdown are original to this skill. The product-level R/E/H expectation is from the team's "good docs" definition.
