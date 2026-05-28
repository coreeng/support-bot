---
name: Gap analysis
description: How the skill computes coverage gaps — per-journey verdicts (covered/partial/missing) with reasons, plus descriptive product-level page counts by Diátaxis type. Load once per run, after audience tagging, before the placement map. Produces a coverage report for stakeholder discussion.
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
- **Product-level coverage** — descriptive counts of builder/maintainer-audience pages by Diátaxis type. No flags; no assertion that any tier must be present.

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

#### Step 4 — Determine verdict (counts-driven, no re-judgement)

**The verdict is determined SOLELY by the counts collected in Step 1.** This step counts; it does NOT re-evaluate whether a tagged match "really counts" or is "too tangential". The journey-matching procedure in `references/journey-matching.md` is the only place where the line between strong, weak, and not-a-match is drawn; that decision is final.

If you find yourself reasoning "these matches are tangential, so I'll exclude them" or "the weak matches don't really cover the journey" — **STOP**. The page is in `weak_how_to` because the journey-matching step decided it was a partial-coverage match; that is exactly what `weak` means. Re-judging here breaks consistency with the per-page tags emitted in REPORT.md Sections 8 and 9 (and would, for the same journey, produce a Section 3 row that contradicts the per-page tags shown elsewhere in the same report).

Apply the following rules in order. The first rule that matches wins.

1. `strong_how_to` empty AND `weak_how_to` empty AND `non_how_to` empty → **missing**. Reason: `no matching pages`.
2. `strong_how_to` empty AND `weak_how_to` empty AND `non_how_to` non-empty → **partial**. Reason: `no how-to; only non-how-to types matched: [comma-separated type list]`.
3. `strong_how_to` empty AND `weak_how_to` non-empty → **partial**. Reason: `weak how-to matches only — investigate`.
4. `strong_how_to` non-empty:
   - Compute variation gaps from Step 2: a list of missing variations.
   - Read the end-to-end result from Step 3.
   - If variation gaps OR `end_to_end: no` → **partial**. Reasons: `missing variations: [list]` and/or `not end-to-end: [missing-step sentence]`.
   - Else → **covered**. No reasons.

#### Step 4a — Enumerated reasons (the ONLY allowed values)

The `reasons` field accepts ONLY the following strings (with bracketed placeholders filled in from the data). The skill MUST NOT emit any other reason text.

| Reason string | When |
| --- | --- |
| `no matching pages` | verdict `missing` (rule 1) |
| `no how-to; only non-how-to types matched: [type list]` | verdict `partial` (rule 2) |
| `weak how-to matches only — investigate` | verdict `partial` (rule 3) |
| `missing variations: [variation list]` | verdict `partial` (rule 4, variation gap) |
| `not end-to-end: [missing-step sentence from Step 3]` | verdict `partial` (rule 4, end-to-end check returned `no`) |

Editorial commentary in any other form is **prohibited**. Reasons like `"tangential matches"`, `"page only mentions the topic"`, `"doesn't cover this directly"`, `"only autoscaling docs touch it"` are all forbidden — those are judgements that belong in the journey-matching step, not here. If you cannot express the verdict's justification with one of the five strings above, the verdict logic produced an unexpected state — re-run the verdict procedure rather than inventing a new reason.

#### Step 4b — Mandatory self-consistency check (run before emitting Section 3)

For every journey row in Section 3 Subsection A, verify:

`row.strong_how_to_count` MUST equal the count of pages in REPORT.md Sections 8 (Copied verbatim) and 9 (Rewritten) such that:
- the page's Diátaxis type is `how-to`, AND
- the page's `journeys` column lists this journey at confidence `strong`.

`row.weak_how_to_count` MUST equal the same count restricted to confidence `weak`.

If the counts disagree, the per-page tags in Sections 8/9 are the **ground truth** — re-derive Section 3's counts from them. Do **not** "fix" the disagreement by changing tags in Sections 8/9. The journey-matching step is the only place where journey-relevance tags are decided, and it has already run for the day.

The check must pass for every row. If it cannot pass, the count was wrong, not the tags.

#### Step 4c — Worked example of the bug this prevents

Suppose journey "X" has two pages tagged `(X, weak)` in the journey-matching step, both with Diátaxis type `how-to`.

**Correct verdict**: `partial`, reason `weak how-to matches only — investigate` (rule 3 above).

**Incorrect verdict**: `missing`, with a freshly-invented reason like `"only tangential pages touch it"`.

The incorrect verdict is wrong on three counts:

1. The journey-matching step already labelled those pages as relevant — that's what `weak` means. The verdict step counts; it does not re-evaluate that label.
2. The reason string isn't in the enumerated list in Step 4a — it's editorial, which is prohibited.
3. The verdict contradicts the per-page tags REPORT.md Sections 8/9 will still emit, breaking cross-section consistency (Step 4b would fail).

If the agent is tempted to add custom reasoning, that's a signal the journey-matching step may have been too generous and the matches should be re-evaluated **there** — not filtered here.

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

### Part B — Product-level coverage (descriptive)

Always runs, regardless of whether `journeys` is empty. Reports the counts of pages at the builder/maintainer audience tier, grouped by Diátaxis type. This is **purely descriptive** — it surfaces how the product-level documentation is composed so stakeholders can see the spread.

The skill does **NOT** assert that every product must have all of reference + explanation + how-to. Whether a missing tier is a problem depends on the product (a small CLI may legitimately have only how-tos; a complex platform usually needs all three). The skill reports what's there and lets the reader judge.

#### Step 1 — Count

Count every scanned page whose `audience.tier = builder/maintainer`, grouped by Diátaxis type:

- `reference_count`
- `explanation_count`
- `how_to_count`
- `tutorial_count`

Outliers do not count (they are not audience-tagged).

#### Step 2 — Record

```yaml
builder_maintainer:
  reference_count: N
  explanation_count: N
  how_to_count: N
  tutorial_count: N
```

No flags. No verdict. No automatic suggested action. The counts stand on their own.

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

A small block showing builder/maintainer audience page counts by Diátaxis type. This is **descriptive only** — the skill makes no assertion that any of these types must be present for a given product. The counts surface the composition so the reader can judge per product.

```
Builder/maintainer audience pages:
- Reference: 12
- Explanation: 5
- How-to: 8
- Tutorial: 2
```

No flags. No verdicts. No automatic suggested actions derive from this subsection.

## What this step does not do

- It does not detect duplication — that is the next step.
- It does not flag unclear, hollow, contradictory, or factually incorrect content — out of scope here.
- It does not suggest actions — that is the synthesis step.
- It does not modify per-page outputs or any earlier output.
- It does not classify or re-classify pages.

## Sources

The journey-coverage verdicts and variation-by-variation breakdown are original to this skill. The product-level Part B is purely descriptive: it reports counts by Diátaxis type at the builder/maintainer audience tier so stakeholders can see the composition without the skill asserting any particular tier must be present.
