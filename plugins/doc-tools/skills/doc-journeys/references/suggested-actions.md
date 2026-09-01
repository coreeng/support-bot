---
name: Suggested actions
description: How the skill synthesises per-step outputs into a single prioritised list of recommended actions for stakeholders. Load once per run, after quality flags, before the placement map. Deterministic mapping; no LLM judgement; no new analysis. Read the "What this step does NOT do" section before adding actions outside the fixed enum.
---

# Suggested actions

This file specifies how the skill turns the outputs of steps 1–6 into a single prioritised list of next actions. It is a **synthesis step**: it does not analyse, judge, or read pages. It maps existing signals to a fixed enum of action types using deterministic rules.

The step runs after quality flags (`${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/quality-flags.md`) and before the placement map is built.

## Action vocabulary

Twelve action types, fixed enum. The skill MUST NOT emit actions outside this list.

Seven of the twelve derive from journey verdicts, duplication clusters or classification verdicts, so **none of them can fire on a product-only run** (`journeys = []`), where `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/audience-tagging.md` also leaves the audience-mismatch subtable empty. The remaining five — `expand-stub`, `clean-stale-markers`, and the three product-scoped types added below — are the only ones a product-only run can emit. Before this was true the section was reliably empty on such runs, and runs responded by writing free-prose lists into it against this file's own rule, which left the section meaning two different things across reports.

| Action type | Severity | Triggered by |
| --- | --- | --- |
| `write-how-to` | high | A journey whose gap-analysis verdict is `missing` (reason `no matching pages`). |
| `complete-how-to` | medium | A journey whose gap-analysis verdict is `partial` AND the reason list contains `missing variations` or `not end-to-end`. |
| `strengthen-how-to` | medium | A journey whose gap-analysis verdict is `partial` AND the reason list contains `weak how-to matches only`. |
| `realign-audience` | medium | Any row in section 3's "Audience mismatches" subtable. |
| `consolidate-cluster` | medium | Any duplication cluster from `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/duplication.md`. |
| `expand-stub` | low | A page carrying the `hollow` quality flag. |
| `clean-stale-markers` | low | A page carrying the `stale-marker` quality flag. |
| `review-rewrite` | low | Every page with a REWRITE-* verdict from classification. |
| `review-split` | low | Every source page that produced a SPLIT verdict. |
| `establish-contact-point` | high | The product page names no corroborated contact point, AND none is reachable within one hop of the pages it links. |
| `correct-declaration` | medium | A field in the product's `product.md` is contradicted by what discovery found. |
| `report-source-defect` | medium | A source-repo or prior-art page this run cited is stale, self-contradictory, or contradicted by a higher-authority source. |

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

### `establish-contact-point`

Fires once per run, on the product page, when BOTH hold after the entity sweep:

- no contact point of any class survived corroboration onto the product page — no channel, email, DL, handle, named person, ticket queue, request form, or owning team; AND
- no contact point is reachable **within one hop**: none of the pages the product page links to names one either.

- Description: `The product page gives a reader no corroborated route to a human; nearest candidate is '<entity>' (<n> corroborating sources), which needs an owner's confirmation before publication.`
- Source reference: the product page path, plus the path and line of the strongest candidate, or `none found` where the sweep produced no candidate at all.

Name the strongest **candidate** and its corroboration count; never the entity the brief asserted, unless corroboration independently reached it. A candidate attributed to the owning *team* rather than to this product is still a candidate — say so in the description, because generalising a team's channel into the product's channel is the error `${CLAUDE_PLUGIN_ROOT}/skills/doc-journeys/references/authoring.md` forbids, and the action exists precisely so a human closes that hop rather than the skill guessing it.

The one-hop test is what keeps this from firing on every page that correctly routes elsewhere: a product page that links to a wiki page naming a channel has given the reader a route, and no action is due.

### `correct-declaration`

For each field in `product-definition/products/<slug>/product.md` that discovery contradicts. The recurring case is `repos`: the declaration omits, or explains away, a repository that then supplies most of the run's cited evidence.

- Description: `'<field>' in product.md states <declared>, but this run's evidence shows <found>. Confirm or amend the declaration.`
- Source reference: `product-definition/products/<slug>/product.md`, plus the section of this report carrying the contradicting evidence (usually the evidence-weight table).

Fires only where the contradiction is **mechanically derivable** — a repo supplying cited evidence but absent from `repos`, a `features` entry no discovery term ever matched. A field that merely looks improvable is not a contradiction and produces nothing.

This action **names an amendment; it does not make one in the same breath.** Where the consumer's `authorisations` file permits it, a `product.md` amendment IS applicable by a run — but only through the gated paths (a confirmed plan or a findings package stating the exact diff), never as a side effect of report synthesis. Where the contradiction sits in a `product.md` and this run did not carry the fix through a gate, emit the action; the next run, or the orchestrator's fix loop, may apply it through its own gate. Where the contradiction sits in a **brief or an existing journey declaration**, the action is the terminal output — those stay human-owned, and no confirmation from any reviewer or finding authorises an edit.

### `report-source-defect`

For each source-repo file or prior-art page this run **cited** that is stale, internally contradictory, or contradicted by a higher-authority source — including a conflict this run resolved. A resolved conflict still leaves a defect in the estate: the run picked the right value, and the wrong one is still sitting there for the next reader.

- Description: `'<path>' <states X, contradicted by '<path2>' which states Y | was last updated <date>, before <fact> changed>. This run resolved to <resolution>; the source is unfixed.`
- Source reference: both paths, and the report's Source conflicts section where one exists.

Never fires for a file the run did not cite — the estate is full of stale documents, and this action is scoped to what this run actually relied on. **The pipeline never edits source repositories**, so this action is the terminal output for such a defect, addressed to the owning team.

## Keeping actions live

Every action type MUST be re-derivable from scratch on each run, and MUST disappear on the run after its cause is fixed. Before adding a type, check it: an action that cannot be re-derived rots into a permanent entry that readers learn to skip, which is the failure mode free prose has in this section and the reason the enum is closed.

The three product-scoped types above satisfy this: a published contact point removes `establish-contact-point`, an amended declaration removes `correct-declaration`, a corrected source removes `report-source-defect`.

## Which section a signal belongs in

Two destinations, and the boundary is the enum, not the signal's importance:

- **Suggested actions** — the signal maps to an action type in the vocabulary table. Nothing else, however obviously actionable it looks.
- **Follow-ups** — everything else: observations, open questions, spec conflicts, anything needing a decision the skill cannot derive.

A signal that feels important but matches no type goes to Follow-ups. If that keeps happening for the same class of signal, the fix is to propose a new **type** with a derivation rule — not to write prose into Suggested actions. That is how this section came to mean two different things across reports.

## Output — REPORT.md

A new section "Suggested actions", at **section 16**, after "Skipped pages" and before "Follow-ups". `SKILL.md`'s Executive report format is authoritative on section ordering; this file does not restate it.

> **Changed 2026-08-12.** This file previously specified section 2, "near the top", and mandated an orientation sentence pointing forward to "the analysis sections that follow" — while `SKILL.md` placed the section at 16. Both could not hold, and reports followed `SKILL.md`, leaving the forward-pointing sentence in a backward-pointing position. `SKILL.md` is authoritative. Reports written before this date may carry the old sentence (`foglight.md`, `foglight-agent.md` do); they were wrong when written, not superseded, and are not retrofitted.

The section header is followed by:

1. A short orientation sentence with exact wording: "Synthesised deterministically from the analysis sections above. No new analysis happens here — each action references the section it was derived from." The sources have already been read by this point; do NOT write "the sections that follow" or anything else implying they are still ahead.
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
- 5 `clean-stale-markers` rows → 1 row: `"Review stale markers in 5 pages: changelogs/foglight, changelogs/foglight-cli, …"`

The Source column for a grouped row reads `(N rows in §<section number>)` so readers can locate the underlying detail (e.g. `(8 rows in §6)` for `expand-stub` referencing Quality flags).

**High and medium severity actions are NEVER grouped.** Each is a distinct decision worth its own row. The reason: stakeholders are expected to act on every high/medium item individually, whereas low items are review/cleanup tasks where one decision can cover many.

Already-spec'd exceptions (unchanged): the `review-rewrite` actions and `review-split` actions each collapse to a single pointer row referencing the relevant detail section, regardless of count.

## Output — Summary line

Add one line to section 1 (Summary) of REPORT.md:

`Suggested actions: N (high X · medium Y · low Z).`

## What this step does NOT do

- **Invent actions outside the enum.** If an unusual signal needs an unusual response, the stakeholder uses "Risk and follow-ups", not Suggested actions. See "Which section a signal belongs in" — this holds however actionable the signal looks, and the remedy for a recurring miss is a new type with a derivation rule, never prose in this section.
- **Amend `product-definition/` from this step.** `correct-declaration` names an amendment; applying one happens only through a gated path (confirmed plan or findings package, `product.md` only — briefs and existing journey declarations stay human-owned), never during report synthesis.
- **Edit source repositories.** `report-source-defect` names a defect for its owning team and stops there.
- **Publish an uncorroborated contact point.** `establish-contact-point` reports that no route exists and names the strongest candidate for a human to confirm. It never licenses emitting that candidate onto a page.
- **LLM-judge** severity or wording. The mapping is fixed.
- **Combine signals from one page into a single action.** Each signal a page carries produces its own action — a page that's both `hollow` and has `stale-marker` content yields both an `expand-stub` AND a `clean-stale-markers` action. This is separate from the **row grouping** rule above (a table-display rule that collapses same-type **low-severity** actions across **different pages** into one row).
- **Include outliers.** Section 10 already carries per-outlier handling.
- **Modify per-page outputs** or any earlier output.

## Sources

The synthesis pattern and action vocabulary are original to this skill. The severity calibration (gaps in journey-level how-to = high) reflects this skill's only hard coverage expectation: every supplied journey should have a matching how-to.
