---
name: Quality flags
description: How the skill flags pages with hollow content or explicit stale markers. Load once per run, after duplication detection, before the placement map. Intentionally simple: two deterministic checks, no LLM judgement. Read the "What this does NOT catch" section before interpreting the output.
---

# Quality flags

This file specifies how the skill flags two kinds of problematic pages: **hollow pages** (stubs, mostly empty) and **pages with stale markers** (explicit `deprecated` / `TODO` / `FIXME` content).

It runs after duplication detection (`references/duplication.md`) and before the placement map is built.

This step is **intentionally minimal**, by the same logic as duplication detection. Two deterministic checks, no LLM judgement. Read the "What this does NOT catch" section before interpreting the output — the simplicity comes at a cost and the cost is explicit by design.

## The two flags

### `hollow`

A page is hollow if **all** of the following are true:

- Its content body (excluding frontmatter, HTML comments, blank lines, and lines that contain only a heading) has fewer than 10 non-blank lines.
- It contains no fenced code blocks (no triple-backtick markers).
- It contains no tables (no lines beginning with `|`).

The rule is calibrated to catch genuine stubs without false-positiving short reference pages — a short reference page almost always carries a table or a code snippet, so requiring "no code AND no tables" filters out legitimate brief references.

### `stale-marker`

A page carries a stale marker if any of the following keywords appear anywhere in its content (case-insensitive match):

- `deprecated`
- `obsolete`
- `TODO:` (with the trailing colon)
- `FIXME:` (with the trailing colon)
- `XXX:` (with the trailing colon)
- `legacy`
- `do not use`

Each match contributes one entry to the page's reason list, with the matching line number and the matched keyword.

**False positives are expected.** `deprecated` may appear as a code identifier; `legacy` may be the name of a module; `TODO:` may be a quoted example in a guide about using TODO comments. The skill does **not** attempt to disambiguate. False positives are visible — the matching line appears in the report — and the stakeholder dismisses them per row.

## Multiple flags per page

A page can be both `hollow` AND carry stale markers. Both flags are recorded; the page appears once in the report with all flags listed in the Flags column and all reasons in the Reasons column.

## What this catches

- Pages that exist but are nearly empty (stubs with a title and a sentence; pages that were started but never finished).
- Pages with explicit deprecation, TODO, FIXME, or "do not use" content that the author left in the file.

## What this does NOT catch (deliberately out of scope for this version)

The following are documented here so stakeholders reading the report know the limits — the absence of a flag does **not** prove a page is fine.

- **Vague prose** — pages that are well-formed but say nothing useful. Detecting this requires LLM judgement and is deferred.
- **Factual contradictions between pages.** "Incorrect" is hard to define without an oracle of correctness; deferred.
- **Stale-by-context** — a page that doesn't mention "deprecated" but is implicitly outdated (refers to a removed component, uses an old API shape, mentions a version no longer in use). Requires LLM judgement and repo context; deferred.
- **Style / tone inconsistency** — pages that don't match a documented style guide. Gated on a style guide being explicitly defined; deferred.
- **Low-confidence Diátaxis classification.** Already flagged in REPORT.md's "Risk and follow-ups" section; not duplicated here.
- **Drift between Diátaxis types.** Already handled by REWRITE/SPLIT verdicts in classification.

If any of these matter for your stakeholder review, they require richer signals than this step provides.

## Procedure

For each scanned page (regardless of journey-match status, audience tier, or Diátaxis verdict):

1. Apply the `hollow` rule. Record a flag if it fires, with a one-line reason.
2. Apply the `stale-marker` keyword scan. Record one reason entry per matching line.
3. If at least one flag fired, the page appears in the report.

Outliers are **not** excluded — an outlier may still be a hollow page or carry a stale marker, and that signal is useful.

## Output — REPORT.md

A new section "Quality flags", placed at the end of the exec block of REPORT.md, immediately after "Duplication candidates" and before the "Detail for reviewers" divider that opens the engineer block. See `SKILL.md`'s Executive report format for the full section ordering.

The section header is followed by:

1. A short reminder of the two flags and what they do not cover — one or two sentences for stakeholders reading the report in isolation.
2. The flag table. Columns:
   - **Source path** — the page's path relative to the repo root.
   - **Flags** — comma-separated (`hollow`, `stale-marker`, or both).
   - **Reasons** — for `hollow`, a one-line summary (`X non-blank content lines; no code or tables`); for `stale-marker`, one entry per match (`line 12: deprecated`, `line 47: TODO:`).

Sort: pages with both flags first; then `hollow`-only; then `stale-marker`-only; ties broken by source path order.

If no pages were flagged, the section contains the single line "No hollow pages or stale markers detected." Do not omit the section.

## What this step does not do

- It does not modify per-page outputs (no frontmatter changes).
- It does not classify, re-classify, or re-tag pages.
- It does not suggest actions — that is the synthesis step.
- It does not analyse cross-page relationships.

## Sources

The two-flag rule is original to this skill. The stale-marker keyword list is a conventional subset of TODO and deprecation conventions widely used in software projects.
