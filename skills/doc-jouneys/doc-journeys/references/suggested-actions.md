---
name: Suggested actions
description: How the skill synthesises per-step outputs into a single prioritised list of recommended actions for stakeholders. Load once per run, after quality flags, before the placement map. Deterministic mapping; no LLM judgement; no new analysis. Read the "What this step does NOT do" section before adding actions outside the fixed enum.
---

# Suggested actions

This file specifies how the skill turns the outputs of steps 1–6 into a single prioritised list of next actions. It is a **synthesis step**: it does not analyse, judge, or read pages. It maps existing signals to a fixed enum of action types using deterministic rules.

The step runs after quality flags (`references/quality-flags.md`) and before the placement map is built.

## Action vocabulary

Nine action types, fixed enum. The skill MUST NOT emit actions outside this list.

| Action type | Severity | Triggered by |
| --- | --- | --- |
| `write-how-to` | high | A journey whose gap-analysis verdict is `missing` (reason `no matching pages`). |
| `complete-how-to` | medium | A journey whose gap-analysis verdict is `partial` AND the reason list contains `missing variations` or `not end-to-end`. |
| `strengthen-how-to` | medium | A journey whose gap-analysis verdict is `partial` AND the reason list contains `weak how-to matches only`. |
| `realign-audience` | medium | Any row in section 3's "Audience mismatches" subtable. |
| `consolidate-cluster` | medium | Any duplication cluster from `references/duplication.md`. |
| `expand-stub` | low | A page carrying the `hollow` quality flag. |
| `clean-stale-markers` | low | A page carrying the `stale-marker` quality flag. |
| `review-rewrite` | low | Every page with a REWRITE-* verdict from classification. |
| `review-split` | low | Every source page that produced a SPLIT verdict. |

A page may produce multiple actions if it carries multiple signals: a `hollow` page with `stale-marker` content yields two actions; a REWRITE page with `stale-marker` yields two; a page in two duplicate clusters and carrying a quality flag yields three. **No collapsing.**

## Severity tiers

Three tiers, fixed:

- `high` — gaps that block this skill's journey-level coverage expectation (every supplied journey should have at least one matching how-to).
- `medium` — drift signals where docs exist but need work (incomplete coverage, audience mismatch, duplication).
- `low` — review prompts and cleanup tasks.

The severity for each action type is fixed in the vocabulary table above. The skill does **not** LLM-judge severity, override the table, or interpolate.

## Derivation rules (one per action type)

### `write-how-to`

For each journey in Part A of gap analysis with `verdict == missing`:

- Description: `Write a how-to for journey '<journey name>' (currently missing: no matching pages).`
- Source reference: the journey name.

### `complete-how-to`

For each journey in Part A with `verdict == partial` AND reasons include `missing variations` or `not end-to-end`:

- Description: `Complete the how-to for journey '<journey name>': <comma-joined matching reasons>.`
- Source reference: the journey name.

### `strengthen-how-to`

For each journey in Part A with `verdict == partial` AND reasons include `weak how-to matches only`:

- Description: `Strengthen the how-to coverage for journey '<journey name>': existing matches are weak — investigate and confirm or rewrite.`
- Source reference: the journey name.

### `realign-audience`

For each row in section 3's "Audience mismatches" subtable:

- Description: `Realign '<source path>' to audience [<journey labels>] (journey says [<journey labels>]; inferred is [<inferred labels>]).`
- Source reference: the source path.

### `consolidate-cluster`

For each duplication cluster:

- Description: `Consolidate or dismiss cluster (journey '<journey name>', <type>, variation <variation or 'none'>): <count> pages — pick a canonical or confirm complementary.`
- Source reference: `cluster-<index>` (1-indexed across the run).

### `expand-stub`

For each page with the `hollow` flag:

- Description: `Expand or remove the stub at '<source path>' (<hollow reason>).`
- Source reference: the source path.

### `clean-stale-markers`

For each page with the `stale-marker` flag:

- Description: `Review stale markers in '<source path>': <matched keywords with line numbers>.`
- Source reference: the source path.

### `review-rewrite`

For every page with a REWRITE-* verdict:

- Description: `Review the rewrite at '<output path>' against source '<source path>' for fidelity.`
- Source reference: the source path.

### `review-split`

For every source page that produced a SPLIT verdict:

- Description: `Review the split of '<source path>' into <count> outputs for unit-boundary correctness.`
- Source reference: the source path.

## Output — REPORT.md

A new section "Suggested actions", placed near the top of REPORT.md immediately after "Summary" and before "Coverage analysis". This high placement is deliberate: stakeholders scanning the report should see the action list first, then dive into the supporting sections below as needed. See `SKILL.md`'s Executive report format for the full section ordering.

The section header is followed by:

1. A short orientation sentence with exact wording: "Synthesised deterministically from the analysis sections that follow. No new analysis happens here — this section is placed near the top so stakeholders see the action list first; each action references its source section below." Do NOT write "prior sections" or anything that implies the sources have already been read; at section 2, the sources are still ahead.
2. **A "Top 3 risks" list** — see "Top 3 risks list" below.
3. The full action table. Columns:
   - **Severity** — `high` / `medium` / `low`.
   - **Type** — the action-type slug.
   - **Description** — the one-line description per the derivation rules.
   - **Source** — the source reference (path, journey name, or cluster ID).

Sort: by severity (`high` first, then `medium`, then `low`); within each severity tier, preserve source order (journeys in input order; pages in scan order; clusters in their numeric index order).

If no actions were emitted, omit the "Top 3 risks" list and let the section contain the single line "No suggested actions — no signals from prior sections produced an action."

### Top 3 risks list

A compact markdown list of the three highest-priority action descriptions, rendered between the orientation sentence and the full action table.

Format:

```markdown
**Top 3 risks**

- 🔴 {description of action 1}
- 🔴 {description of action 2}
- 🟡 {description of action 3}
```

Each list item carries a stoplight emoji corresponding to its severity:
- `high` → 🔴
- `medium` → 🟡
- `low` → 🟢

**Selection rule**: take the first 3 rows of the sorted action list (already sorted high → medium → low, then by source order). If there are fewer than 3 actions total, list whatever exists. The list never contains more than 3 items.

**No narrative.** Do NOT add user-impact explanation, reasoning, or expanded prose. The description is the action verb only, exactly as it appears in the full table. If grouping (see below) has collapsed multiple low-severity actions into a single row, the Top 3 list reuses the grouped description verbatim — it does not re-expand the group.

### Row grouping (low-severity actions only)

When two or more **low-severity** actions in the full action table share the same `type`, collapse them into a single row. The description follows this template:

`<canonical verb> the {count} {noun(s)}: <comma-separated source references>`

Examples:

- 8 `expand-stub` rows → 1 row: `"Expand or remove the 8 hollow stubs: p2p/extended-test/p2p-extended-test, p2p/fast-feedback/p2p-functional, …"`
- 5 `clean-stale-markers` rows → 1 row: `"Review stale markers in 5 pages: changelogs/core-platform, changelogs/corectl, …"`

The Source column for a grouped row reads `(N rows in §<section number>)` so readers can locate the underlying detail (e.g. `(8 rows in §6)` for `expand-stub` referencing Quality flags).

**High and medium severity actions are NEVER grouped.** Each is a distinct decision worth its own row. The reason: stakeholders are expected to act on every high/medium item individually, whereas low items are review/cleanup tasks where one decision can cover many.

Already-spec'd exceptions (unchanged): the `review-rewrite` actions and `review-split` actions each collapse to a single pointer row referencing the relevant detail section, regardless of count.

## Output — Summary line

Add one line to section 1 (Summary) of REPORT.md:

`Suggested actions: N (high X · medium Y · low Z).`

## What this step does NOT do

- **Invent actions outside the enum.** If an unusual signal needs an unusual response, the stakeholder uses "Risk and follow-ups", not Suggested actions.
- **LLM-judge** severity or wording. The mapping is fixed.
- **Combine signals from one page into a single action.** Each signal a page carries produces its own action — a page that's both `hollow` and has `stale-marker` content yields both an `expand-stub` AND a `clean-stale-markers` action. This is separate from the **row grouping** rule above (a table-display rule that collapses same-type **low-severity** actions across **different pages** into one row).
- **Include outliers.** Section 10 already carries per-outlier handling.
- **Modify per-page outputs** or any earlier output.

## Sources

The synthesis pattern and action vocabulary are original to this skill. The severity calibration (gaps in journey-level how-to = high) reflects this skill's only hard coverage expectation: every supplied journey should have a matching how-to.
