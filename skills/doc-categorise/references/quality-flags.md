---
name: Quality flags
description: How the skill flags pages with hollow content or explicit stale markers. Load once per run, after duplication detection, before the placement map. The hollow check is a pure deterministic check; the stale-marker check is a cheap deterministic keyword prefilter followed by a bounded adjudication step that discards incidental keyword mentions. Read the "What this does NOT catch" section before interpreting the output.
---

# Quality flags

This file specifies how the skill flags two kinds of problematic pages: **hollow pages** (stubs, mostly empty) and **pages with stale markers** (explicit `deprecated` / `TODO` / `FIXME` content that says *this doc is stale*).

It runs after duplication detection (`references/duplication.md`) and before the placement map is built.

This step is **kept deliberately cheap**. The `hollow` check is a pure deterministic rule. The `stale-marker` check leads with a deterministic keyword prefilter — the fast part that reduces the whole doc set to a handful of candidate lines — and then adjudicates only those candidates so that a keyword the page merely *talks about* (a how-to about migrating off legacy repos) is not reported as if the page itself were stale. Read the "What this does NOT catch" section before interpreting the output — the checks are narrow by design.

## The two flags

### `hollow`

A page is hollow if **all** of the following are true:

- Its content body (excluding frontmatter, HTML comments, blank lines, and lines that contain only a heading) has fewer than 10 non-blank lines.
- It contains no fenced code blocks (no triple-backtick markers).
- It contains no tables (no lines beginning with `|`).

The rule is calibrated to catch genuine stubs without false-positiving short reference pages — a short reference page almost always carries a table or a code snippet, so requiring "no code AND no tables" filters out legitimate brief references.

### `stale-marker`

A page carries a stale marker if it contains a keyword that genuinely signals the page (or a feature/API/command it documents) is deprecated, superseded, or not to be used. The check runs in two stages: a cheap deterministic **candidate scan**, then a bounded **adjudication** that keeps only genuine markers and discards incidental keyword mentions.

The two stages exist to separate the two costs. The scan is what makes the check fast — it reduces the whole doc set to a handful of candidate lines before any judgement runs. The adjudication is what makes it *useful* — it prevents a page that merely *talks about* deprecation (e.g. a how-to whose task is migrating off legacy repos) from being reported as if the page itself were stale.

#### Stage 1 — candidate scan (deterministic)

Scan every page for these keywords (case-insensitive match):

- `deprecated`
- `obsolete`
- `TODO:` (with the trailing colon)
- `FIXME:` (with the trailing colon)
- `XXX:` (with the trailing colon)
- `legacy`
- `do not use`

Each match is a **candidate**, recorded with its line number, the matched keyword, and the line text. Candidates are not yet reasons — they must survive Stage 2.

#### Stage 2 — adjudication

Resolve each candidate to `marker` (genuine) or `incidental` (dismissed) in two tiers, cheapest first. Do the cheap tier first so the LLM tier only ever sees what the deterministic rules could not settle.

**Tier A — deterministic auto-dismiss (no LLM).** A candidate is `incidental` if the match falls inside any of:

- a fenced code block (between triple-backtick markers) or an inline-code span (backtick-delimited);
- a URL, a file path, or a dotted/underscored/camel-cased code identifier (e.g. `legacy_config`, `LegacyClient`, `deprecated.md`).

**Tier B — batched judgement (LLM), applied only to candidates that survive Tier A.** Collect every surviving candidate across the whole run and resolve them in a **single** pass — one call, not one per line. For each candidate give the model the line, ~2 lines of surrounding context, and the page's title/topic. The page content is already in context from classification, so this adds one round-trip regardless of doc-set size. Classify each candidate:

- `marker` — the text tells a reader that *this* page, feature, API, or command is deprecated / obsolete / superseded / not-to-be-used: an admonition or banner ("⚠️ This page is deprecated — see X"), a "use Y instead" instruction, a `deprecated: true` frontmatter flag, a `TODO:`/`FIXME:` the author left in the prose.
- `incidental` — the keyword is the **subject the page is documenting**, not a status of the page: a how-to whose task *is* migrating off legacy repos, a reference entry describing a `legacy` mode, a guide about writing TODO comments, or prose where the word is the grammatical object ("migrate your **legacy** artifacts to …").

The deciding question the model applies to each candidate: *is the doc telling me this doc/thing is stale, or is staleness the topic it is documenting?* Only the former is a `marker`.

Only `marker` candidates raise the `stale-marker` flag and contribute a reason. `incidental` candidates are dismissed, but their count is retained so the filtering stays auditable (see Output).

**Residual false positives.** Adjudication greatly reduces false positives but does not eliminate them — a genuinely ambiguous line may still be miscalled either way. Every reported marker still shows its line in the report, so a reviewer can confirm or dismiss per row.

## Multiple flags per page

A page can be both `hollow` AND carry stale markers. Both flags are recorded; the page appears once in the report with all flags listed in the Flags column and all reasons in the Reasons column.

## What this catches

- Pages that exist but are nearly empty (stubs with a title and a sentence; pages that were started but never finished).
- Pages carrying an explicit deprecation, TODO, FIXME, or "do not use" marker that says *this page/thing is stale* — after incidental mentions of those keywords have been filtered out by adjudication.

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
2. Run the `stale-marker` **Stage 1** candidate scan and **Tier A** deterministic auto-dismiss per page. Collect the surviving candidates.
3. Run the `stale-marker` **Tier B** adjudication **once for the whole run** over all surviving candidates (a single batched call). For each page, record one reason entry per candidate resolved to `marker`; tally the candidates resolved to `incidental`.
4. A page carries the `stale-marker` flag only if it has at least one `marker` reason. If at least one flag (`hollow` or `stale-marker`) fired, the page appears in the report.

Outliers are **not** excluded — an outlier may still be a hollow page or carry a stale marker, and that signal is useful.

## Output — REPORT.md

A new section "Quality flags", placed at the end of the exec block of REPORT.md, immediately after "Duplication candidates" and before the "Detail for reviewers" divider that opens the engineer block. See `SKILL.md`'s Executive report format for the full section ordering.

The section header is followed by:

1. A short reminder of the two flags and what they do not cover — one or two sentences for stakeholders reading the report in isolation.
2. The flag table. Columns:
   - **Source path** — the page's path relative to the repo root.
   - **Flags** — comma-separated (`hollow`, `stale-marker`, or both).
   - **Reasons** — for `hollow`, a one-line summary (`X non-blank content lines; no code or tables`); for `stale-marker`, one entry per genuine marker (`line 12: deprecated`, `line 47: TODO:`).

Sort: pages with both flags first; then `hollow`-only; then `stale-marker`-only; ties broken by source path order.

If any keyword candidates were dismissed as incidental during adjudication, append a single italic line directly below the table recording the tally, so the filtering is auditable — e.g. `_9 keyword hits across 3 pages were scanned and dismissed as incidental (the keyword is the page's subject, not a staleness marker)._` A page whose only stale-marker candidates were all dismissed does **not** appear in the table on the strength of those candidates.

If no pages were flagged, the section contains the single line "No hollow pages or stale markers detected." (still followed by the dismissed-candidates line if any were dismissed). Do not omit the section.

## What this step does not do

- It does not modify per-page outputs (no frontmatter changes).
- It does not classify, re-classify, or re-tag pages.
- It does not suggest actions — that is the synthesis step.
- It does not analyse cross-page relationships.

## Sources

The two-flag rule is original to this skill. The stale-marker keyword list is a conventional subset of TODO and deprecation conventions widely used in software projects.
