---
name: Topic coverage
description: Optional present/missing analysis of expected topics per journey. Extracts a canonical topic list from each journey's description prose via an LLM call, then judges per (topic, journey-matched-page) pair whether the topic is covered. Emits Part A4 of the Journey relevance summary. Runs in all modes including `coverage-only`. Non-deterministic by design — the topic list and matching verdict may shift between runs.
---

# Topic coverage

This step extends the Journey relevance summary with a new table (**Part A4**) that lists the expected sub-topics of each journey and marks each one `present` or `missing` based on the pages matched to that journey. The step is **mandatory** when `journeys` is non-empty — there is no opt-out. It runs in every execution mode, including `coverage-only`, because topic coverage is a primary journey-relevance signal.

The step is purely descriptive: no suggested actions are emitted from missing-topic verdicts. The reader judges what the gaps mean for their roadmap.

## Determinism

This step is **not deterministic**. Both the topic extraction and the per-topic present/missing verdict use LLM calls and may produce different results between runs even with identical inputs. Two consequences:

1. The Part A4 table MUST carry a literal preamble line: `_Topics extracted via LLM from each journey's description; verdicts use LLM judgement against matched pages. Both may vary between runs._`
2. Cross-section consistency invariants in SKILL.md do NOT apply to Part A4. The topic list is not derived from per-page tags; the verdict is not derived from a deterministic rule. Other report sections MUST NOT cite Part A4 as ground truth for anything.

## Input — journey description prose

Topics are extracted from each journey file's full markdown body (frontmatter excluded). The `description:` frontmatter field is used as a fallback only when the body is empty.

No schema change is required on `product-definition/journeys/*.md`. The skill is read-only with respect to these files.

## Procedure

### Pass 1 — Topic extraction

For each journey in the input list, **one LLM call**:

- **Input**: the journey's `name`, `description` (frontmatter), and full markdown body.
- **Output constraints**:
  - Return between **3 and 8** topics. Fewer than 3 means the prose is too thin to support topic analysis; more than 8 means the topics are too granular.
  - Each topic is a short verb-noun phrase, 2–7 words, sentence-case, no trailing punctuation. Examples: `Request instance with size and version`, `Configure backups and PITR`, `Decommission instance safely`.
  - Topics MUST be derivable from the prose. Do not invent topics the prose does not mention.
  - Topics MUST be distinct: do not return two topics that paraphrase the same concept.
  - Topics MUST NOT include the journey name itself (e.g. for "Provision a Postgres database", do not return "Provision a Postgres database" as a topic).
- **Output format**: a JSON array of strings, held in memory; not written to disk.

If the LLM returns fewer than 3 valid topics, record the journey under "Journeys with insufficient prose for topic extraction" in the run summary and skip Pass 2 for that journey. Its Part A4 block shows: `_Insufficient prose to extract topics._`

### Pass 2 — Topic-to-page matching

For each journey with a valid topic list and at least one matched page, **one LLM call per journey** (not per topic — batched for efficiency):

- **Input**:
  - The topic list from Pass 1.
  - For every page matched to the journey (strong + weak; both tiers participate): the page's path, title (H1 or frontmatter `title`), first H2 heading if present, and the first 500 characters of body content after frontmatter stripping.
- **Output constraints**:
  - For each topic, return `present` or `missing`.
  - If `present`, return the list of page paths that substantively cover the topic. "Substantively" means the page contains explicit prose, steps, examples, or reference material about the topic — a passing mention or single link is `missing`.
  - A single page may be evidence for multiple topics.
- **Output format**: a JSON object keyed by topic, with `{status: "present"|"missing", evidence: [path, ...]}` per entry; held in memory.

If a journey has zero matched pages, every topic is `missing` with empty evidence. No LLM call is made for Pass 2 on that journey.

## Output — REPORT.md

### Part A4 — Topic coverage per journey *(always when `journeys` is non-empty)*

Position in the Part A sequence: **A1 → A2 → A3 → A4 → A5**. A4 precedes A5 (per-journey page index, specified in `references/journey-matching.md`).

Columns: `Journey | Topic | Status | Evidence`.

One row per (journey, topic) pair, grouped by journey in input-list order, topics within a journey ordered as Pass 1 returned them (do not re-sort). Status cell holds `present` or `missing`. Evidence cell holds a comma-separated list of page paths when `present`, or `—` when `missing`.

The table is preceded by the determinism preamble line (verbatim, see Determinism section above).

When `journeys = []`, Part A4 is omitted entirely (same rule as Parts A1/A2/A3).

When a journey's topic extraction returned fewer than 3 valid topics, that journey contributes a single row: `<journey-name> | _Insufficient prose to extract topics._ | — | —`.

## Interaction with weighting and gap analysis

Part A4 is intentionally orthogonal to Parts A2/A3 and to the coverage verdicts in Section 3. Specifically:

- A journey marked `covered` in Section 3 may still have `missing` topics in Part A4. Coverage is page-count-based; topic coverage is content-based.
- A journey with a `present` topic count below `Min topics` from `weightings.md` is **not** flagged. The Min/Max columns are about page counts, not topic counts; the two metrics are distinct.
- A `missing` topic does NOT trigger any suggested action in `references/suggested-actions.md`. The action enum is fixed; this step is descriptive only.

## What this does NOT do

- **Does not extract topics from page titles.** Topics come from journey prose, not from the docs being audited. If you want to surface which page titles map to which journeys, that is Part A1.
- **Does not score topic coverage quality.** A `present` verdict means at least one page covers the topic substantively; it says nothing about whether the coverage is good, current, or complete.
- **Does not deduplicate topics across journeys.** Two journeys may have overlapping topics; each journey's row set is independent.
- **Does not check Diátaxis-type appropriateness per topic.** A topic marked `present` may be covered only by a Reference page when a How-to would be more useful. That nuance is left to the reader.
- **Does not persist the topic list.** Each run extracts afresh. To pin topics across runs, the user should add an explicit `topics:` schema to journey files — a future enhancement, not in scope here.

## Failure modes

- **LLM returns malformed JSON**: log under "Topic extraction errors: <journey-name>" in the run summary; that journey's Part A4 block shows `_Topic extraction failed; skipped._`. Do not retry — the cost is not worth the noise. The rest of the step continues for other journeys.
- **LLM Pass 2 omits a topic from its response**: treat as `missing` with empty evidence. Log under "Topic matching gaps: <journey-name>" in the run summary.
- **LLM returns evidence paths that don't match any actual matched page**: drop the unknown paths silently and use the remaining valid ones. If the resulting evidence list is empty for a `present` verdict, downgrade to `missing`.
