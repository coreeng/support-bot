---
name: diataxis-scoring
description: Use when reviewing documentation for Diataxis-framework alignment, scoring how well a doc or directory supports a specific user journey for a product, auditing mode-purity (tutorial/how-to/reference/explanation), or measuring doc relevance to a product workflow.
---

# Documentation scoring

## Overview

Scores documentation along two complementary axes:

1. **Diataxis purity** — does each page hold a single mode (tutorial / how-to / reference / explanation)? Mixing confuses readers. ([Diataxis framework](https://diataxis.fr/).)
2. **Journey-fit** — given a user journey for a product, does the doc actually get a user through it?

Diataxis runs from the doc alone. Journey-fit needs the product and journey from the user.

## The four Diataxis modes

| Mode | Reader's question | Signals |
|------|-------------------|---------|
| Tutorial | "How do I get started?" | Lesson framing, "you'll learn", beginner narrative, results shown |
| How-to | "How do I do X?" | Imperative verbs, numbered steps, commands |
| Reference | "What is X?" | Field/param tables, type signatures, structured listings |
| Explanation | "Why is X?" | "Why"/"because", conceptual prose, trade-offs |

## Inputs

- **Always**: a file path, directory, or pasted content.
- **For journey-fit**: the **product** (e.g., "Insights", "Core Platform") and the **journey** as a single-sentence user goal (e.g., "Submit an insight questionnaire to surface a team's product delivery state").

If the user asks for journey-fit (mentions "journey", "user journey", or a product) but doesn't supply both, **ask before scoring**:

> "To score journey-fit, please give me (1) the product, and (2) the journey as a single-sentence user goal."

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

**Single file**:
1. **Infer 3–7 natural steps** the user must accomplish to complete the journey (e.g., for "Submit an insight questionnaire…": find it, fill it, submit it, see the surfaced state).
2. Walk every line; classify as **On-journey** (helps perform a step), **Off-journey** (unrelated), or **Skip**.
3. Compute:
   - Relevance = `round(on-journey / (on-journey + off-journey) × 100)`
   - Coverage = `round(addressed-steps / total-steps × 100)`
4. Journey-fit = `round((relevance + coverage) / 2)`.

**Collection**: Relevance aggregates line-weighted; Coverage counts a step as addressed if any file addresses it; final = `round((agg-relevance + collection-coverage) / 2)`.

## Output — single file

Always emit the Diataxis block:

```
**Diataxis score**: <N>/100
**Dominant mode**: <Tutorial | How-to | Reference | Explanation>
**Lines counted**: <fit> fit + <violation> violating (<skipped> skipped)
**Chief drift**: lines <a–b> read as <other mode>
```

Omit `Chief drift` when no contiguous off-mode block exists. If journey-fit was requested, append:

```
**Journey-fit score**: <N>/100
**Product**: <product>
**Journey**: <journey>
**Relevance**: <N>/100 (<on-journey> on-journey + <off-journey> off-journey)
**Coverage**: <N>/100 (<addressed> of <total> steps; missing: <step list>)
```

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

If journey-fit was requested, append a matching block:

```
**Collection journey-fit score**: <N>/100
**Product**: <product>  **Journey**: <journey>
**Relevance**: <N>/100  **Coverage**: <N>/100  (missing: <steps>)

| File | Relevance | Coverage |
|------|-----------|----------|
| <path> | <N> | <N> |
```

Sort tables by score ascending so weakest pages surface first.
