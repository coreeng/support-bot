---
name: diataxis-scoring
description: Use when reviewing documentation for Diataxis-framework alignment, scoring how well a doc or directory helps a user achieve one or more product user-journeys, auditing mode-purity (tutorial/how-to/reference/explanation), or measuring doc relevance to product workflows.
---

# Documentation scoring

## Overview

Scores documentation along two complementary axes:

1. **Diataxis purity** — does each page hold a single mode (tutorial / how-to / reference / explanation)? Mixing confuses readers. ([Diataxis framework](https://diataxis.fr/).)
2. **Journey-fit** — given one or more user journeys for a product, **would this doc help the user achieve the journey?**

Diataxis runs from the doc alone. Journey-fit needs the user to name the product(s) and journey(s).

## The four Diataxis modes

| Mode | Reader's question | Signals |
|------|-------------------|---------|
| Tutorial | "How do I get started?" | Lesson framing, "you'll learn", beginner narrative, results shown |
| How-to | "How do I do X?" | Imperative verbs, numbered steps, commands |
| Reference | "What is X?" | Field/param tables, type signatures, structured listings |
| Explanation | "Why is X?" | "Why"/"because", conceptual prose, trade-offs |

## Inputs

- **Always**: a file path, directory, or pasted content.
- **For journey-fit**: one or more **(product, journey)** pairs. Each journey is a single-sentence user goal (e.g., "Submit an insight questionnaire to surface a team's product delivery state"). The product is the named container the journey belongs to (e.g., "Insights", "Core Platform").

If the user asks for journey-fit (mentions "journey", "user journey", or a product) but doesn't supply both pieces for every journey, **ask before scoring**:

> "To score journey-fit, please give me one or more (product, journey) pairs. Each journey should be a single-sentence user goal, like 'Submit an insight questionnaire to surface a team's product delivery state'."

## Skip-list (used by both axes)

Blank lines, frontmatter delimiters (`---`), lone heading markers with no content, TOC entries, "see also" / license boilerplate.

## Diataxis algorithm

**Single file**:
1. Pick the **dominant mode** by reading the file end-to-end.
2. Walk every line; classify as **Fit** (serves dominant mode — code lines inherit their section's mode), **Violation** (different mode), or **Skip**.
3. Score = `round(fit / (fit + violation) × 100)`.

**Collection**: line-weighted aggregate (not a flat mean):
`score = round(total-fit / (total-fit + total-violation) × 100)`.

## Journey-fit algorithm

The central question per journey: **would this doc help the user achieve this journey?**

**Single file, single journey**:
1. **Infer 3–7 natural steps** the user must accomplish to complete the journey (e.g., for "Submit an insight questionnaire…": find it, fill it, submit it, see the surfaced state).
2. Walk every line; classify as **On-journey** (helps perform a step), **Off-journey** (unrelated), or **Skip**.
3. Compute:
   - Relevance = `round(on-journey / (on-journey + off-journey) × 100)`
   - Coverage = `round(addressed-steps / total-steps × 100)`
4. Journey-fit = `round((relevance + coverage) / 2)`.

**Multiple journeys**: score the doc against each journey independently — never combine journeys into one number. Different journeys probe different content.

**Collection**: Relevance aggregates line-weighted across files; Coverage counts a step as addressed if any file addresses it; final = `round((agg-relevance + collection-coverage) / 2)`. Repeat per journey.

## Output — single file

Always emit the Diataxis block:

```
**Diataxis score**: <N>/100
**Dominant mode**: <Tutorial | How-to | Reference | Explanation>
**Lines counted**: <fit> fit + <violation> violating (<skipped> skipped)
**Chief drift**: lines <a–b> read as <other mode>
```

Omit `Chief drift` when no contiguous off-mode block exists. If journey-fit was requested, append one block per (product, journey) pair, or — for several journeys — a single table:

```
**Journey-fit**

| Product | Journey | Score | Relevance | Coverage | Missing steps |
|---------|---------|-------|-----------|----------|---------------|
| <product> | <journey> | <N> | <N> | <N> | <step list> |
```

Sort rows by Score ascending — weakest journey-fit first.

## Output — collection

Always emit the Diataxis block:

```
**Collection Diataxis score**: <N>/100
**Files scored**: <count>
**Counted lines**: <fit> fit + <violation> violating (<skipped> skipped)

| File | Mode | Score |
|------|------|-------|
| <path> | <mode> | <N> |
```

If journey-fit was requested, append — for each (product, journey) pair — a collection-level summary plus a per-file breakdown:

```
**Collection journey-fit** — <product> · <journey>
**Score**: <N>/100  **Relevance**: <N>/100  **Coverage**: <N>/100  (missing: <steps>)

| File | Score | Relevance | Coverage |
|------|-------|-----------|----------|
| <path> | <N> | <N> | <N> |
```

When several journeys are scored, repeat the block per journey (one per heading). Sort tables by Score ascending so weakest pages surface first.
