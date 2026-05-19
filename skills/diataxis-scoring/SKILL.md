---
name: diataxis-scoring
description: Use when reviewing documentation for Diataxis-framework alignment, auditing whether a page holds a single mode (tutorial/how-to/reference/explanation), or measuring mode-purity across a docs directory.
---

# Diataxis scoring

## Overview

[Diataxis](https://diataxis.fr/) says each documentation page should serve exactly one of four modes: **tutorial**, **how-to**, **reference**, or **explanation**. Mixing modes confuses readers. This skill scores mode-purity by counting the lines that drift off the dominant mode.

## The four modes

| Mode | Reader's question | Signals |
|------|-------------------|---------|
| Tutorial | "How do I get started?" | Lesson framing, "you'll learn", beginner narrative, concrete results shown |
| How-to | "How do I do X?" | Imperative verbs, numbered steps, commands, "to X, do Y" |
| Reference | "What is X?" | Field/param/env-var tables, type signatures, dry structured listings |
| Explanation | "Why is X?" | "Why"/"because", conceptual prose, trade-offs, design rationale |

## Inputs

File path, directory (scored per file then aggregated), or pasted content.

## Single file — algorithm

1. Pick the **dominant mode** by reading the file end-to-end.
2. Walk every line and classify:
   - **Fit** — serves the dominant mode. Code lines inside a code block inherit their section's mode.
   - **Violation** — belongs to a different Diataxis mode.
   - **Skip** — blank lines, frontmatter delimiters (`---`), lone heading markers, TOC entries, "see also" / license boilerplate.
3. Score = `round(fit / (fit + violation) × 100)`.

## Collection — algorithm

Apply the file algorithm to each file, then aggregate **line-weighted** (not a flat per-file mean):

`collection-score = round(total-fit / (total-fit + total-violation) × 100)`

## Output — single file

```
**Diataxis score**: <N>/100
**Dominant mode**: <Tutorial | How-to | Reference | Explanation>
**Lines counted**: <fit> fit + <violation> violating (<skipped> skipped)
**Chief drift**: lines <a–b> read as <other mode>
```

Omit `Chief drift` when no contiguous off-mode block exists.

## Output — collection

```
**Collection Diataxis score**: <N>/100
**Files scored**: <count>
**Counted lines**: <fit> fit + <violation> violating (<skipped> skipped)

| File | Mode | Score |
|------|------|-------|
| <path> | <mode> | <N> |
```

Sort rows by score ascending so the weakest pages surface first.
