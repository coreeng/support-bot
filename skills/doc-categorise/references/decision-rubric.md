---
name: Decision rubric — ambiguous cases
description: Fixed-procedure, judgement-magnitude rubric for pages the compass cannot resolve alone. Covers split, outlier, low-confidence, and user-escalation paths.
---

# Decision rubric

This file activates when the compass returns an ambiguous answer. Apply the procedure below **in order**. Do not skip steps. The *order* of checks is fixed; the *magnitudes* ("substantial", "dominant", "comparable") are judgement calls — use the doc set's own baseline as your reference rather than absolute thresholds.

## When to load this file

Load this when at least one of these is true:

- The compass returns two plausible types.
- The reader-question test (see `compass.md`) produces more than one clear answer.
- The page has a title suggesting one type and content suggesting another.
- The page contains material from more than one type in substantial measure.
- The page does not fit any of the four types.

If the compass returns a single type confidently and the content matches the signals in `types.md`, this file is not needed.

## The procedure

### Step 1 — Sample the page

For a short page (< 100 lines), classify the whole page.

For a longer page, sample three to five self-contained sections. Apply the compass to each independently and record the result. The page-level classification is derived from the section-level results in step 4.

For a page that is one section only — by design or by neglect — treat it as a single sample.

### Step 2 — Count signals

For the page (or each sampled section), count the strength of these four signal families. The order below is the order in which to evaluate them, but every family must be counted, not just the first one that produces a hit.

1. **Imperative-step signals** — numbered steps, "run X", "set Y", branching by environment, prerequisites.
2. **Declarative-machinery signals** — tables of fields/flags/options, definition lists, command synopsis blocks, identical sub-headings repeated for each entry.
3. **Discursive-prose signals** — paragraphs of continuous prose, "because", "historically", trade-off framing, comparative language, conceptual diagrams.
4. **Pedagogical-voice signals** — "we'll", "you'll see", "let's", reassurance cues, a promised end-state, a closing celebration.

Count by **page weight**, not occurrence count: a single ten-line table is heavier than a single one-line table. Estimate the fraction of the page (or section) given over to each family. Fractions should sum to roughly the page; the remainder is incidental scaffolding (titles, frontmatter, links).

### Step 3 — Determine the dominant signal

Compare the four signal weights. There are three possible outcomes:

- **One signal substantially dominant.** One family clearly outweighs the others. Classify per the family:
  | Dominant family | Classification |
  | --- | --- |
  | Imperative-step + pedagogical-voice together | Tutorial |
  | Imperative-step *without* pedagogical-voice | How-to guide |
  | Declarative-machinery | Reference |
  | Discursive-prose | Explanation |

  Then verify against the anti-signals in `types.md`. If any anti-signal hits, drop back to step 2 and recount — you likely undercounted a competing family.

- **Two signals roughly comparable.** Each is meaningful (each ≥ ~25% of page weight). The page is multi-intent → SPLIT (see below).

- **No family is meaningful.** None of the four families holds substantial page weight. The page is either an OUTLIER, a stub, or scaffolding (see below).

### Step 4 — Reconcile sampled sections (multi-section pages only)

If you sampled multiple sections in step 1:

- If all sections classify as the same type → classify the page as that type.
- If sections classify as different types but one type covers ≥ 75% of the page's weight → classify as that type; if the off-type sections are substantive, note them in the report so a human reviewer can decide whether to split later.
- If sections classify as different types and no type covers ≥ 75% → SPLIT.

### Step 5 — Resolve title-vs-content conflict

If the title suggests one type but the signal count suggests another, **trust the signals**. Record the conflict in the report under "Risk and follow-ups" so a human reviewer can decide whether the page should be re-titled.

Title-vs-content conflicts are common in legacy docs (e.g. a page titled "Tutorial: X" that is really a how-to). Do not let the title override what the page actually does.

## Magnitude language

The procedure uses three magnitude words deliberately. They are judgement calls — use the doc set's average as your baseline.

- **Substantial** — meaningful enough to read like part of the page's purpose, not an aside. As a rough guide: ≥ 25% of page weight.
- **Dominant** — outweighs each of the other families by a clear margin. As a rough guide: ≥ 1.5× the next-largest family. Calibrate to the doc set: if the doc set is uniformly table-heavy, the threshold for "dominant reference" should be higher.
- **Comparable** — within roughly the same band. As a rough guide: within ~30% of each other.

If two doc sets give different absolute numbers for "dominant", that is fine. The procedure is reproducible; the calibration is local.

## SPLIT rules

When the verdict is SPLIT, the source page produces multiple output files. There is no upper limit — a sprawling README may produce ten or more outputs. **Each output is strictly single-type.**

### Step 1 — Identify unit boundaries

Walk the source page heading by heading. A **unit** is a self-contained body of content with a single goal, subject, or scope. Boundary signals:

- An H2 (or H1, on flat pages) that introduces a new topic distinct from what came before.
- A shift in voice (e.g. tutorial voice ends; reference voice begins).
- A heading whose title is goal-shaped or topic-shaped and has its own steps/fields/prose underneath.

Subsections belong to their parent unit, not to separate units. `## Install on Linux` with sub-sections `### via apt` and `### via tarball` is **one** unit (with two paths), not two units.

### Step 2 — Classify each unit independently

Run the Classification procedure (compass + `types.md`, escalating to this rubric if needed) on each unit. Each unit yields one of the standard verdicts:

- **PERFECT** — extract the unit as-is into the matching subfolder. Normalise heading levels if needed (the unit's top heading becomes H1 in the output).
- **REWRITE** — rewrite the unit per the rewrite rules in `SKILL.md`.
- **SPLIT** — the unit itself blends intents. Apply this procedure recursively. Recursive SPLIT is bounded by the source's heading depth, so termination is guaranteed.
- **OUTLIER** — the unit does not fit any type; record in the report and do not place.

### Step 3 — Group same-type units only when they are variants of one goal

After classifying, if two or more units share a type, decide grouping by **topic coherence**:

- **Variants of one goal** (e.g. install on Linux / macOS / Windows; or three options of one command) → group into a single output file with sub-sections. The output's title is the shared goal ("How to install Foglight").
- **Independent goals** (e.g. install / configure logging / set up monitoring) → keep as separate output files. Each gets its own title.

When in doubt, keep them separate. Joining unrelated tasks makes the output longer than necessary; the cost of an extra file is small, and navigation in a Diataxis tree handles many small files well.

### Step 4 — Name each output

Use the unit's natural heading to derive a slug (lowercase, dash-separated, ASCII). Examples:

- `## Install on Linux` → `how-to/install-on-linux.md`
- `## The foglight agent command` → `reference/foglight-agent.md`
- A unit grouping three install variants → `how-to/install.md` (with `### Linux`, `### macOS`, `### Windows` sub-sections inside)

If the unit has no natural title (e.g. the only how-to portion of an otherwise single-type source), fall back to the source basename: `how-to/<basename>.md`. Collisions are handled by the rule in `SKILL.md` (path-derived slug suffix).

### Step 5 — Preserve every fact

Across all outputs from a SPLIT, every fact, command, code block, link, and example from the source must appear in at least one output. **No fact disappears.** Cross-check by listing facts before and after the split.

If a fact applies to multiple outputs (e.g. a prerequisite that all of the how-tos share), place it in the most-authoritative output — typically reference — and link from the others, rather than duplicating verbatim.

### Step 6 — Cross-link outputs

Every output from a SPLIT carries a "See also" footer listing its siblings:

```
## See also
- [How to install Foglight](../how-to/install.md)
- [About Foglight's release cadence](../explanation/release-cadence.md)
- [foglight agent reference](../reference/foglight-agent.md)
```

Use the unit's natural title in the link text, not the slug. Link to all siblings from the same SPLIT; for large splits (more than ~6 siblings), group the See-also links by type (How-tos / References / Explanations).

### Step 7 — Record the split

Add an entry to `REPORT.md` under "Split" with:

- Source path.
- Each resulting output path with its type.
- A one-line reason summarising the split decision — for example: "10 independent source sections → 8 outputs (3 install variants grouped as one how-to; 4 independent how-tos; 2 references; 1 explanation)".

## OUTLIER rules

When the verdict is OUTLIER:

- Do **not** generate a placement file under any of the four categories.
- Add an entry to `REPORT.md` under "Outliers (no Diátaxis fit)" with:
  - Source path
  - Why it does not fit (which signals or anti-signals failed, what kind of doc it actually is)
  - Suggested handling — examples:
    - Release notes / changelog → keep in place, link from explanation if conceptual.
    - Meeting minutes / decision logs → move to `decisions/` or similar; not user-facing docs.
    - FAQ that is genuinely Q&A reference → repackage as reference; otherwise outlier.
    - ToC / landing pages → preserve as navigation; not user-facing content.
    - Marketing / pitch material → not in scope.

Outliers are an expected output of the rubric, not a failure. A doc set with zero outliers is suspicious.

## Low-confidence cases

If you classify but confidence is low — for example, the dominant signal beats the next-largest by a hair, or the page is short enough that signal counting is noisy:

- **Place the file with your best-guess type.** Do not refuse to classify low-confidence pages; that produces silence in the report and a worse review experience than a flagged guess.
- Flag the file in `REPORT.md` under "Risk and follow-ups" with the source path, the chosen type, the runner-up type, and a one-line reason ("close call: imperative steps and discursive prose roughly comparable, chose how-to because the title is goal-shaped").

Low-confidence is the common case for a legacy doc set. **Do not escalate to the user for every close call** — users come to this skill because they do not know Diátaxis well; asking them which type a page should be defeats the purpose. The `REPORT.md` "Risk and follow-ups" flag is what they review after the run, not a stream of in-flight questions.

## Escalation to the user

Escalate to the user **only in extreme cases**. All three of these must be true:

- The signal counts in step 3 give two types within ~10% of each other in page weight, AND
- The page is foundational — linked from many other docs, or named in the source repo's top-level index, AND
- A guess would meaningfully change downstream navigation.

For a typical repo this should fire on at most a handful of pages, not dozens. If you find yourself escalating frequently, you are over-applying the criteria — drop back to "place + flag" and keep moving.

When you do escalate, present the scoring, not just the question. Format:

> Page: `docs/onboarding/x.md`. Imperative steps: ~40%. Discursive prose: ~38%. Tables: ~15%. Pedagogical voice: ~7%. Closest two types: how-to guide and explanation. Which type should I place this under, or should I split?

Never escalate without showing the scoring. "What type is this?" without context is not useful to the reviewer.

## Edge cases

### Very short pages (< 30 lines)

Signal counting is noisy on short pages. This threshold is independent of the < 100-line threshold in Step 1 of "The procedure": that threshold decides *whether to multi-sample*; this one flags *noise in signal counting*. If the page is short:

- If it documents a single command or field → reference.
- If it gives a single recipe with a goal-shaped title → how-to.
- If it is fewer than ~10 lines of content → likely a stub or scaffolding → outlier; flag the source repo for a missing doc.

### Very long pages (> 500 lines)

Long pages are almost always multi-intent. Sample five or more sections in step 1. Expect a SPLIT verdict from step 4 unless one type clearly dominates.

### Pages that are mostly code

If the page is dominated by code blocks with thin prose:

- Code with declarative captions ("This command lists pods.") → reference.
- Code with imperative captions ("Run this to list pods.") → how-to.
- Code following numbered learning steps → tutorial.
- Code without surrounding intent → ambiguous; apply step 2 to whatever prose exists.

### Pages with frontmatter `diataxis_type` already set

If the source already declares a type, treat it as a hint, not authority. Run the rubric anyway. If the declared type matches the rubric's verdict, proceed; if it does not, trust the rubric and flag the conflict in the report.

## Sources

This rubric extends Diátaxis with a specific procedure for ambiguous cases. The base concepts (the four types, signal families) are from <https://diataxis.fr/>; the procedural framing is original to this skill.
