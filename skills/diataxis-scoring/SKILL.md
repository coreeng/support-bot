---
name: diataxis-scoring
description: Use when reviewing a doc or collection of docs that belong to one product, checking Diataxis-framework alignment and whether the doc covers one or more user-journeys for that product; reports doc mode-purity (tutorial / how-to / reference / explanation) and a covered/partially/not-covered verdict per journey.
---

# Documentation scoring

## Overview

Two independent checks over the same doc(s):

1. **Diataxis purity** — does each page hold a single mode (tutorial / how-to / reference / explanation)? Mixing confuses readers. ([Diataxis framework](https://diataxis.fr/).)
2. **Journey coverage** — for each named user journey, **would this doc help the user achieve the journey?** A simple verdict: covered, partially covered, or not covered. No percentage.

## Required inputs

The skill scores docs that belong to **one product**. Multiple journeys are allowed but they must all belong to that same product.

- **The doc(s)** — a file path, directory, or pasted content.
- **The product** — exactly one (e.g., "Insights", "Core Platform").
- **One or more journeys for that product** — each a single-sentence user goal (e.g., "Submit an insight questionnaire to surface a team's product delivery state").

All three are mandatory. If any is missing, **ask before doing anything**:

> "Please give me (1) the product these docs belong to, and (2) one or more journeys for that product. Each journey should be a single-sentence user goal, like 'Submit an insight questionnaire to surface a team's product delivery state'."

If the user supplies journeys from multiple products in one invocation, decline and ask them to split into separate invocations — one per product.

## The four Diataxis modes

| Mode | Reader's question | Signals |
|------|-------------------|---------|
| Tutorial | "How do I get started?" | Lesson framing, "you'll learn", beginner narrative, results shown |
| How-to | "How do I do X?" | Imperative verbs, numbered steps, commands |
| Reference | "What is X?" | Field/param tables, type signatures, structured listings |
| Explanation | "Why is X?" | "Why"/"because", conceptual prose, trade-offs |

## Skip-list (used by both checks)

Blank lines, frontmatter delimiters (`---`), lone heading markers with no content, TOC entries, "see also" / license boilerplate.

## Diataxis algorithm

**Single file**:
1. Pick the **dominant mode** by reading the file end-to-end.
2. Walk every line; classify as **Fit** (serves dominant mode — code inherits its section's mode), **Violation** (different mode), or **Skip**.
3. Score = `round(fit / (fit + violation) × 100)`.

**Collection**: aggregate line-weighted (not a flat mean):
`score = round(total-fit / (total-fit + total-violation) × 100)`.

## Journey coverage check

For each supplied journey (all within the single product):

1. **Infer 3–7 natural steps** the user must accomplish to achieve the journey (e.g., for "Submit an insight questionnaire…": find it, fill it in, submit it, see the surfaced state).
2. For each step, decide: does the doc (or any file in the collection) address it? **Yes or no — no math.**
3. Emit a **verdict**:
   - **Covered** — all steps addressed
   - **Partially covered** — some steps addressed
   - **Not covered** — no steps addressed
4. Always list the missing steps so the author can see the gap.

## Output

First, the Diataxis block. For a single file:

```
**Diataxis score**: <N>/100
**Dominant mode**: <Tutorial | How-to | Reference | Explanation>
**Lines counted**: <fit> fit + <violation> violating (<skipped> skipped)
**Chief drift**: lines <a–b> read as <other mode>
```

Omit `Chief drift` when no contiguous off-mode block exists. For a collection use this variant (one row per file, sorted by score ascending):

```
**Collection Diataxis score**: <N>/100
**Files scored**: <count>
**Counted lines**: <fit> fit + <violation> violating (<skipped> skipped)

| File | Mode | Score |
|------|------|-------|
| <path> | <mode> | <N> |
```

Then the journey-coverage block — product as a header label, one row per journey:

```
**Journey coverage** — <product>

| Journey | Verdict | Steps addressed | Steps missing |
|---------|---------|-----------------|---------------|
| <journey> | <Covered / Partially / Not> | <step list> | <step list or "—"> |
```

Sort rows by verdict severity (Not covered first, then Partially, then Covered) so the largest gaps surface first.
